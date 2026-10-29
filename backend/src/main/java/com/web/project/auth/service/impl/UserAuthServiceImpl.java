package com.web.project.auth.service.impl;

import com.web.project.auth.dto.RefreshTokenDTO;
import com.web.project.auth.dto.ResetPasswordDTO;
import com.web.project.auth.dto.SendCodeDTO;
import com.web.project.auth.dto.UserLoginDTO;
import com.web.project.auth.dto.UserRegisterDTO;
import com.web.project.auth.model.AccountType;
import com.web.project.auth.model.TargetType;
import com.web.project.auth.model.VerificationScene;
import com.web.project.auth.protection.PasswordVerificationGuard;
import com.web.project.auth.service.AuthSessionService;
import com.web.project.auth.service.UserAuthService;
import com.web.project.auth.service.VerificationCodeService;
import com.web.project.auth.util.ContactNormalizer;
import com.web.project.auth.util.PasswordPolicy;
import com.web.project.auth.vo.AccessTokenVO;
import com.web.project.auth.vo.TokenPairVO;
import com.web.project.channel.model.AuthChannelConfig;
import com.web.project.channel.service.AuthChannelService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.id.SnowflakeIdGenerator;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.SecureRandom;

/**
 * 普通用户认证业务实现类。
 *
 * 普通用户使用手机号或邮箱注册、登录，成功后调用公共会话服务签发双 Token。
 */
@Service
public class UserAuthServiceImpl implements UserAuthService {

    private static final int MAX_USER_NO_ATTEMPTS = 5;
    private static final String USER_NO_INDEX = "uk_user_account_user_no";
    private static final String PHONE_INDEX = "uk_user_account_phone";
    private static final String EMAIL_INDEX = "uk_user_account_email";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;
    private final VerificationCodeService verificationCodeService;
    private final AuthChannelService authChannelService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final PasswordVerificationGuard passwordVerificationGuard;

    @Autowired
    public UserAuthServiceImpl(
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder,
            AuthSessionService authSessionService,
            VerificationCodeService verificationCodeService,
            AuthChannelService authChannelService,
            SnowflakeIdGenerator snowflakeIdGenerator,
            PasswordVerificationGuard passwordVerificationGuard
    ) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
        this.verificationCodeService = verificationCodeService;
        this.authChannelService = authChannelService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.passwordVerificationGuard = passwordVerificationGuard;
    }

    @Override
    public void sendCode(SendCodeDTO dto, Long currentUserId) {
        verificationCodeService.send(dto, currentUserId);
    }

    /**
     * 注册并返回双 Token。
     */
    @Override
    @Transactional
    public TokenPairVO register(UserRegisterDTO registerDTO) {
        TargetType targetType = registerDTO.targetType();
        String target = ContactNormalizer.normalize(
                targetType,
                registerDTO.target()
        );

        AuthChannelConfig channels = authChannelService.getChannels();

        if (!channels.enabled(targetType)) {
            throw new BusinessException(ErrorCode.AUTH_CHANNEL_DISABLED);
        }

        if (findAccount(targetType, target) != null) {
            throw new BusinessException(ErrorCode.TARGET_ALREADY_REGISTERED);
        }

        String password = registerDTO.password();

        if (password != null && !password.isEmpty()) {
            PasswordPolicy.validate(password);
        }

        verificationCodeService.verify(
                VerificationScene.REGISTER,
                targetType,
                target,
                registerDTO.code()
        );

        UserAccount account = new UserAccount();

        if (targetType == TargetType.PHONE) {
            account.setPhone(target);
        } else {
            account.setEmail(target);
        }

        if (password != null && !password.isEmpty()) {
            account.setPassword(passwordEncoder.encode(password));
        }

        account.setId(snowflakeIdGenerator.nextId());
        insertAccount(account);

        // 与数据库中 password_version 的初始值一致。
        return authSessionService.createSession(
                AccountType.USER,
                account.getId(),
                0L
        );
    }

    /**
     * 密码或验证码登录。
     */
    @Override
    public TokenPairVO login(UserLoginDTO loginDTO) {
        TargetType targetType = loginDTO.targetType();
        String target = ContactNormalizer.normalize(
                targetType,
                loginDTO.target()
        );

        boolean hasPassword = loginDTO.password() != null
                && !loginDTO.password().isEmpty();
        boolean hasCode = loginDTO.code() != null;

        // 密码与验证码必须且只能提供一个
        if (hasPassword == hasCode) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "密码和验证码必须且只能提供一个"
            );
        }

        if (hasPassword) {
            PasswordPolicy.validateForLogin(loginDTO.password());
        }

        AuthChannelConfig channels = authChannelService.getChannels();

        if (!channels.enabled(targetType)) {
            throw new BusinessException(ErrorCode.AUTH_CHANNEL_DISABLED);
        }

        UserAccount account = findAccount(targetType, target);

        // 账号不存在和密码错误使用同一提示，避免暴露账号是否存在。
        if (account == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!Integer.valueOf(1).equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        if (hasPassword) {
            // 没有密码的账号只能使用验证码登录
            if (account.getPassword() == null
                    || !passwordVerificationGuard.matches(
                            passwordEncoder,
                            loginDTO.password(),
                            account.getPassword()
                    )) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
        } else {
            if (!channels.codeEnabled(targetType)) {
                throw new BusinessException(ErrorCode.CODE_FUNCTION_DISABLED);
            }

            verificationCodeService.verify(
                    VerificationScene.LOGIN,
                    targetType,
                    target,
                    loginDTO.code()
            );
        }

        return authSessionService.createSession(
                AccountType.USER,
                account.getId(),
                account.getPasswordVersion()
        );
    }

    @Override
    public AccessTokenVO refresh(RefreshTokenDTO refreshTokenDTO) {
        return authSessionService.refresh(
                refreshTokenDTO.refreshToken(),
                AccountType.USER
        );
    }

    @Override
    public void logout(String sessionId) {
        authSessionService.logout(AccountType.USER, sessionId);
    }

    /**
     * 忘记密码：校验验证码后重置密码。
     */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordDTO resetPasswordDTO) {
        TargetType targetType = resetPasswordDTO.targetType();
        String target = ContactNormalizer.normalize(
                targetType,
                resetPasswordDTO.target()
        );

        AuthChannelConfig channels = authChannelService.getChannels();

        if (!channels.enabled(targetType)) {
            throw new BusinessException(ErrorCode.AUTH_CHANNEL_DISABLED);
        }

        UserAccount account = findAccount(targetType, target);

        if (account == null) {
            throw new BusinessException(ErrorCode.TARGET_NOT_REGISTERED);
        }

        PasswordPolicy.validate(resetPasswordDTO.newPassword());

        verificationCodeService.verify(
                VerificationScene.RESET_PASSWORD,
                targetType,
                target,
                resetPasswordDTO.code()
        );

        /*
         * 更新密文并递增 password_version 在同一条 UPDATE 中完成，
         * 旧 Refresh Token 因版本不一致无法续期。
         */
        int updated = userAccountMapper.updatePassword(
                account.getId(),
                passwordEncoder.encode(resetPasswordDTO.newPassword())
        );
        if (updated != 1) {
            throw new BusinessException(ErrorCode.TARGET_NOT_REGISTERED);
        }
    }

    /**
     * 创建账号；user_no 唯一索引冲突时重新生成，最多尝试 5 次。
     */
    private void insertAccount(UserAccount account) {
        for (int attempt = 0; attempt < MAX_USER_NO_ATTEMPTS; attempt++) {
            account.setUserNo(generateUserNo());

            try {
                if (userAccountMapper.insert(account) != 1) {
                    throw new IllegalStateException("创建用户账号未写入预期记录");
                }
                return;
            } catch (DuplicateKeyException exception) {
                if (!isUniqueIndexConflict(exception, USER_NO_INDEX)) {
                    if (isUniqueIndexConflict(exception, PHONE_INDEX)
                            || isUniqueIndexConflict(exception, EMAIL_INDEX)) {
                        throw new BusinessException(ErrorCode.TARGET_ALREADY_REGISTERED, exception);
                    }
                    throw exception;
                }
                if (attempt == MAX_USER_NO_ATTEMPTS - 1) {
                    throw new BusinessException(
                            ErrorCode.INTERNAL_SERVER_ERROR,
                            "生成用户号码失败，请稍后重试",
                            exception
                    );
                }
            }
        }
    }

    private boolean isUniqueIndexConflict(DuplicateKeyException exception, String index) {
        // 只识别 MySQL 错误末尾的索引名，重复值本身也可能包含相同文本。
        String message = exception.getMostSpecificCause().getMessage();
        return message != null
                && (message.endsWith("for key '" + index + "'") || message.endsWith("." + index + "'"));
    }

    private UserAccount findAccount(TargetType targetType, String target) {
        return targetType == TargetType.PHONE
                ? userAccountMapper.selectByPhone(target)
                : userAccountMapper.selectByEmail(target);
    }

    /**
     * 生成随机 6 位数字用户号码。
     */
    private String generateUserNo() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }
}
