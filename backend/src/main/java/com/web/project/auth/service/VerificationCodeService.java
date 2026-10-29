package com.web.project.auth.service;

import com.web.project.auth.code.CodeSender;
import com.web.project.auth.code.VerificationCodeStore;
import com.web.project.auth.dto.SendCodeDTO;
import com.web.project.auth.model.TargetType;
import com.web.project.auth.model.VerificationScene;
import com.web.project.auth.util.ContactNormalizer;
import com.web.project.channel.model.AuthChannelConfig;
import com.web.project.channel.service.AuthChannelService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * 验证码业务类。
 *
 * 负责发送流程的渠道校验、场景资格判断、发送锁、每日次数和发送，
 * 以及校验流程的原子消费。
 */
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private static final int MAX_DAILY_SENDS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeStore codeStore;
    private final AuthChannelService authChannelService;
    private final UserAccountMapper userAccountMapper;
    private final CodeSender codeSender;

    /**
     * 发送验证码。
     *
     * @param dto           发送参数
     * @param currentUserId 当前登录用户 ID；仅换绑场景需要，匿名场景为 null
     */
    public void send(SendCodeDTO dto, Long currentUserId) {
        TargetType targetType = dto.targetType();
        VerificationScene scene = dto.scene();
        String target = ContactNormalizer.normalize(targetType, dto.target());

        AuthChannelConfig channels = authChannelService.getChannels();

        requireCodeEnabled(channels, targetType);

        checkSceneEligibility(scene, targetType, target, currentUserId);

        // 资格通过后再占用发送额度；同一场景和目标每 60 秒只允许发送一次。
        if (!codeStore.acquireSendLock(scene, targetType, target)) {
            throw new BusinessException(ErrorCode.CODE_SEND_TOO_FREQUENT);
        }

        // 每日额度按目标累计，不能通过切换验证码场景绕过上限。
        long count = codeStore.incrementDailyCount(targetType, target);

        if (count > MAX_DAILY_SENDS) {
            codeStore.releaseSendLock(scene, targetType, target);
            throw new BusinessException(ErrorCode.CODE_SEND_LIMIT_EXCEEDED);
        }

        String code = generateCode();
        codeStore.saveCode(scene, targetType, target, code);

        /*
         * 发送失败时直接返回服务器错误，
         * Redis key 等待 TTL 自动过期，不执行补偿删除。
         */
        codeSender.send(targetType, target, code);
    }

    /**
     * 校验并消费验证码。
     *
     * @param scene      验证码场景
     * @param targetType 联系方式类型
     * @param target     规范化后的联系方式
     * @param code       用户提交的验证码
     */
    public void verify(
            VerificationScene scene,
            TargetType targetType,
            String target,
            String code
    ) {
        requireCodeEnabled(authChannelService.getChannels(), targetType);

        if (!codeStore.consumeCode(scene, targetType, target, code)) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID);
        }
    }

    private void checkSceneEligibility(
            VerificationScene scene,
            TargetType targetType,
            String target,
            Long currentUserId
    ) {
        switch (scene) {
            case REGISTER -> {
                if (findAccount(targetType, target) != null) {
                    throw new BusinessException(ErrorCode.TARGET_ALREADY_REGISTERED);
                }
            }
            case LOGIN, RESET_PASSWORD -> {
                if (findAccount(targetType, target) == null) {
                    throw new BusinessException(ErrorCode.TARGET_NOT_REGISTERED);
                }
            }
            case REBIND_OLD -> {
                UserAccount account = requireCurrentAccount(currentUserId);
                String current = targetType == TargetType.PHONE
                        ? account.getPhone()
                        : account.getEmail();

                if (!target.equals(current)) {
                    throw new BusinessException(
                            ErrorCode.BAD_REQUEST,
                            "旧联系方式不属于当前账号"
                    );
                }
            }
            case REBIND_NEW -> {
                requireCurrentAccount(currentUserId);

                if (findAccount(targetType, target) != null) {
                    throw new BusinessException(ErrorCode.TARGET_ALREADY_BOUND);
                }
            }
        }
    }

    private void requireCodeEnabled(AuthChannelConfig channels, TargetType targetType) {
        if (!channels.enabled(targetType)) {
            throw new BusinessException(ErrorCode.AUTH_CHANNEL_DISABLED);
        }

        if (!channels.codeEnabled(targetType)) {
            throw new BusinessException(ErrorCode.CODE_FUNCTION_DISABLED);
        }
    }

    private UserAccount findAccount(TargetType targetType, String target) {
        return targetType == TargetType.PHONE
                ? userAccountMapper.selectByPhone(target)
                : userAccountMapper.selectByEmail(target);
    }

    private UserAccount requireCurrentAccount(Long currentUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }

        UserAccount account = userAccountMapper.selectById(currentUserId);

        if (account == null) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }

        if (!Integer.valueOf(1).equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        return account;
    }

    private String generateCode() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }
}
