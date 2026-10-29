package com.web.project.user.service.impl;

import com.web.project.auth.dto.ResetPasswordDTO;
import com.web.project.auth.model.TargetType;
import com.web.project.auth.model.VerificationScene;
import com.web.project.auth.service.VerificationCodeService;
import com.web.project.auth.util.ContactNormalizer;
import com.web.project.auth.util.PasswordPolicy;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.user.dto.RebindEmailDTO;
import com.web.project.user.dto.RebindPhoneDTO;
import com.web.project.user.dto.UpdateProfileDTO;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import com.web.project.user.service.UserProfileService;
import com.web.project.user.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.IntSupplier;

/**
 * 普通用户个人资料业务实现类。
 *
 * 换绑依次消费旧、新验证码；校验失败或 MySQL 事务回滚不会恢复已消费的验证码。
 */
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;

    @Override
    public UserInfoVO getProfile(Long userId) {
        UserAccount account = requireActiveAccount(userId);

        return new UserInfoVO(
                account.getId(),
                account.getUserNo(),
                account.getPhone(),
                account.getEmail(),
                account.getNickname(),
                account.getAvatar(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }

    @Override
    public void updateProfile(Long userId, UpdateProfileDTO updateProfileDTO) {
        requireActiveAccount(userId);

        int updated = userAccountMapper.updateProfile(
                userId,
                updateProfileDTO.nickname(),
                updateProfileDTO.avatar()
        );
        requireUpdatedAccount(updated);
    }

    /**
     * 登录态修改密码。
     *
     * 与忘记密码共用密码更新逻辑，但目标联系方式必须属于当前用户。
     */
    @Override
    @Transactional
    public void changePassword(Long userId, ResetPasswordDTO resetPasswordDTO) {
        TargetType targetType = resetPasswordDTO.targetType();
        String target = ContactNormalizer.normalize(
                targetType,
                resetPasswordDTO.target()
        );

        UserAccount account = requireActiveAccount(userId);
        requireContactBelongsToAccount(account, targetType, target);
        PasswordPolicy.validate(resetPasswordDTO.newPassword());

        verificationCodeService.verify(
                VerificationScene.RESET_PASSWORD,
                targetType,
                target,
                resetPasswordDTO.code()
        );

        int updated = userAccountMapper.updatePassword(
                userId,
                passwordEncoder.encode(resetPasswordDTO.newPassword())
        );
        requireUpdatedAccount(updated);
    }

    @Override
    @Transactional
    public void rebindPhone(Long userId, RebindPhoneDTO rebindPhoneDTO) {
        TargetType oldTargetType = rebindPhoneDTO.oldTargetType();
        String oldTarget = ContactNormalizer.normalize(
                oldTargetType,
                rebindPhoneDTO.oldTarget()
        );
        String newPhone = ContactNormalizer.normalize(
                TargetType.PHONE,
                rebindPhoneDTO.newPhone()
        );

        UserAccount account = requireActiveAccount(userId);
        requireContactBelongsToAccount(account, oldTargetType, oldTarget);

        verificationCodeService.verify(
                VerificationScene.REBIND_OLD,
                oldTargetType,
                oldTarget,
                rebindPhoneDTO.oldCode()
        );
        verificationCodeService.verify(
                VerificationScene.REBIND_NEW,
                TargetType.PHONE,
                newPhone,
                rebindPhoneDTO.newCode()
        );

        updateContact(
                () -> userAccountMapper.updatePhone(userId, newPhone)
        );
    }

    @Override
    @Transactional
    public void rebindEmail(Long userId, RebindEmailDTO rebindEmailDTO) {
        TargetType oldTargetType = rebindEmailDTO.oldTargetType();
        String oldTarget = ContactNormalizer.normalize(
                oldTargetType,
                rebindEmailDTO.oldTarget()
        );
        String newEmail = ContactNormalizer.normalize(
                TargetType.EMAIL,
                rebindEmailDTO.newEmail()
        );

        UserAccount account = requireActiveAccount(userId);
        requireContactBelongsToAccount(account, oldTargetType, oldTarget);

        verificationCodeService.verify(
                VerificationScene.REBIND_OLD,
                oldTargetType,
                oldTarget,
                rebindEmailDTO.oldCode()
        );
        verificationCodeService.verify(
                VerificationScene.REBIND_NEW,
                TargetType.EMAIL,
                newEmail,
                rebindEmailDTO.newCode()
        );

        updateContact(
                () -> userAccountMapper.updateEmail(userId, newEmail)
        );
    }

    private void requireContactBelongsToAccount(
            UserAccount account,
            TargetType targetType,
            String target
    ) {
        String current = targetType == TargetType.PHONE
                ? account.getPhone()
                : account.getEmail();

        if (!target.equals(current)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "该联系方式不属于当前账号"
            );
        }
    }

    /**
     * 更新联系方式；唯一键冲突转换为业务错误。
     */
    private void updateContact(IntSupplier update) {
        try {
            requireUpdatedAccount(update.getAsInt());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.TARGET_ALREADY_BOUND, exception);
        }
    }

    private void requireUpdatedAccount(int updated) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }
    }

    private UserAccount requireActiveAccount(Long userId) {
        UserAccount account = userAccountMapper.selectById(userId);

        if (account == null) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }

        if (!Integer.valueOf(1).equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        return account;
    }
}
