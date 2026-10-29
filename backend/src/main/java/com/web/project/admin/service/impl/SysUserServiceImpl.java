package com.web.project.admin.service.impl;

import com.web.project.admin.dto.CreateSysUserDTO;
import com.web.project.admin.dto.ResetSysUserPasswordDTO;
import com.web.project.admin.dto.SysUserQueryDTO;
import com.web.project.admin.dto.UpdateSysUserDTO;
import com.web.project.admin.entity.SysUser;
import com.web.project.auth.model.AccountType;
import com.web.project.auth.presence.AuthPresenceStore;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.admin.service.SysUserService;
import com.web.project.storage.entity.StorageFile;
import com.web.project.storage.mapper.StorageFileMapper;
import com.web.project.storage.service.StorageService;
import com.web.project.admin.vo.SysUserListVO;
import com.web.project.auth.util.PasswordPolicy;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.exception.ExceptionDiagnostics;
import com.web.project.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * 系统用户管理业务实现。
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);

    private final SysUserMapper sysUserMapper;
    private final AuthPresenceStore presenceStore;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final StorageFileMapper storageFileMapper;
    private final TransactionTemplate transactions;

    /**
     * 分页查询系统用户列表。
     */
    @Override
    public PageResult<SysUserListVO> getSysUserPage(SysUserQueryDTO queryDTO) {
        int page = queryDTO.getPage();
        int pageSize = queryDTO.getPageSize();

        // 空白关键词按未提供搜索条件处理。
        String keyword = queryDTO.getKeyword();

        if (keyword != null) {
            keyword = keyword.trim();

            if (keyword.isEmpty()) {
                keyword = null;
            }
        }

        Integer status = queryDTO.getStatus();

        long total = sysUserMapper.countByCondition(keyword, status);

        if (total == 0) {
            return new PageResult<>(0L, page, pageSize, List.of());
        }

        long offset = (long) (page - 1) * pageSize;

        List<SysUser> sysUsers = sysUserMapper.selectPageByCondition(
                keyword, status, offset, pageSize, queryDTO.getCreatedAtOrder());

        // 列表只返回展示字段，密码密文不进入响应。
        Set<Long> onlineIds = presenceStore.onlineIds(AccountType.SYS_USER,
                sysUsers.stream().map(SysUser::getId).toList());
        List<SysUserListVO> records = sysUsers.stream()
                .map(user -> toListVO(user, Integer.valueOf(1).equals(user.getStatus())
                        && onlineIds.contains(user.getId())))
                .toList();

        return new PageResult<>(total, page, pageSize, records);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (Long.valueOf(1L).equals(id) && Integer.valueOf(0).equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "超级管理员不允许停用");
        }
        requireExistingUser(sysUserMapper.updateStatus(id, status));
    }

    @Override
    @Transactional
    public SysUserListVO create(CreateSysUserDTO createDTO) {
        PasswordPolicy.validate(createDTO.password());

        SysUser sysUser = new SysUser();
        sysUser.setUsername(createDTO.username().trim());
        sysUser.setPassword(passwordEncoder.encode(createDTO.password()));
        sysUser.setNickname(createDTO.nickname().trim());
        sysUser.setStatus(createDTO.status());

        try {
            if (sysUserMapper.insert(sysUser) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "登录账号已存在", exception);
        }

        return toListVO(sysUserMapper.selectById(sysUser.getId()));
    }

    @Override
    @Transactional
    public SysUserListVO update(Long id, UpdateSysUserDTO updateDTO) {
        SysUser sysUser = new SysUser();
        sysUser.setId(id);
        sysUser.setUsername(updateDTO.username().trim());
        sysUser.setNickname(updateDTO.nickname().trim());

        try {
            requireExistingUser(sysUserMapper.update(sysUser));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "登录账号已存在", exception);
        }

        return toListVO(sysUserMapper.selectById(id));
    }

    @Override
    @Transactional
    public void resetPassword(Long id, ResetSysUserPasswordDTO resetDTO) {
        PasswordPolicy.validate(resetDTO.password());
        int updated = sysUserMapper.updatePassword(id, passwordEncoder.encode(resetDTO.password()));
        requireExistingUser(updated);
    }

    @Override
    public SysUserListVO updateAvatar(Long id, MultipartFile avatarFile) {
        if (sysUserMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统用户不存在");
        }

        StorageFile uploaded = storageService.storeAvatar(avatarFile);
        AvatarChange change;
        try {
            change = transactions.execute(status -> {
                if (storageFileMapper.selectByIdForUpdate(uploaded.getId()) == null) {
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
                SysUser current = sysUserMapper.selectByIdForUpdate(id);
                if (current == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "系统用户不存在");
                }
                Long previousFileId = current.getAvatarFileId();
                requireExistingUser(sysUserMapper.updateAvatar(id, uploaded.getId()));
                return new AvatarChange(toListVO(sysUserMapper.selectById(id)), previousFileId);
            });
        } catch (RuntimeException exception) {
            try {
                storageService.delete(uploaded.getId());
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }

        try {
            storageService.delete(change.previousFileId());
        } catch (RuntimeException exception) {
            log.warn("Failed to clean up previous avatar file for system user {}; types and stack frames:\n{}",
                    id, ExceptionDiagnostics.withoutMessages(exception));
        }
        return change.user();
    }

    @Override
    public void delete(Long id) {
        if (Long.valueOf(1L).equals(id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "超级管理员不允许删除");
        }
        Long previousFileId = transactions.execute(status -> {
            SysUser current = sysUserMapper.selectByIdForUpdate(id);
            if (current == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "系统用户不存在");
            }
            requireExistingUser(sysUserMapper.deleteById(id));
            return current.getAvatarFileId();
        });
        storageService.delete(previousFileId);
    }

    private record AvatarChange(SysUserListVO user, Long previousFileId) {
    }

    private void requireExistingUser(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统用户不存在");
        }
    }

    private SysUserListVO toListVO(SysUser sysUser) {
        if (sysUser == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return toListVO(sysUser, Integer.valueOf(1).equals(sysUser.getStatus())
                && presenceStore.isOnline(AccountType.SYS_USER, sysUser.getId()));
    }

    private SysUserListVO toListVO(SysUser sysUser, boolean online) {
        if (sysUser == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return new SysUserListVO(
                sysUser.getId(),
                sysUser.getUsername(),
                sysUser.getNickname(),
                StorageService.url(sysUser.getAvatarFileId()),
                sysUser.getStatus(),
                online,
                sysUser.getCreatedAt(),
                sysUser.getUpdatedAt());
    }
}
