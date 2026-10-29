package com.web.project.user.service.impl;

import com.web.project.common.result.PageResult;
import com.web.project.user.dto.UserAccountQueryDTO;
import com.web.project.user.dto.UpdateUserAccountDTO;
import com.web.project.auth.model.TargetType;
import com.web.project.auth.util.ContactNormalizer;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.user.entity.UserAccount;
import com.web.project.auth.model.AccountType;
import com.web.project.auth.presence.AuthPresenceStore;
import com.web.project.user.mapper.UserAccountMapper;
import com.web.project.user.service.UserAccountService;
import com.web.project.user.vo.UserAccountListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 普通用户管理业务实现类。
 */
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountMapper userAccountMapper;
    private final AuthPresenceStore presenceStore;

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (userAccountMapper.updateStatus(id, status) != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在");
        }
    }

    @Override
    @Transactional
    public UserAccountListVO updateUser(Long id, UpdateUserAccountDTO updateDTO) {
        String phone = updateDTO.phone() == null ? null
                : ContactNormalizer.normalize(TargetType.PHONE, updateDTO.phone());
        String email = updateDTO.email() == null ? null
                : ContactNormalizer.normalize(TargetType.EMAIL, updateDTO.email());
        try {
            int updated = userAccountMapper.updateAccount(id, updateDTO.nickname(), phone, email);
            if (updated != 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.TARGET_ALREADY_BOUND, exception);
        }
        UserAccount account = userAccountMapper.selectById(id);
        return toListVO(account, Integer.valueOf(1).equals(account.getStatus())
                && presenceStore.isOnline(AccountType.USER, account.getId()));
    }

    private UserAccountListVO toListVO(UserAccount account, boolean online) {
        return new UserAccountListVO(account.getId(), account.getUserNo(), account.getPhone(), account.getEmail(),
                account.getNickname(), account.getAvatar(), account.getStatus(), online,
                account.getCreatedAt(), account.getUpdatedAt());
    }

    /**
     * 分页查询普通用户列表。
     */
    @Override
    public PageResult<UserAccountListVO> getUserPage(
            UserAccountQueryDTO queryDTO
    ) {
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

        long total = userAccountMapper.countByCondition(
                keyword,
                status
        );

        if (total == 0) {
            return new PageResult<>(
                    0L,
                    page,
                    pageSize,
                    List.of()
            );
        }

        long offset = (long) (page - 1) * pageSize;

        List<UserAccount> userAccounts =
                userAccountMapper.selectPageByCondition(
                        keyword,
                        status,
                        offset,
                        pageSize,
                        queryDTO.getCreatedAtOrder()
                );

        // 列表只返回展示字段，密码与密码版本不进入响应。
        Set<Long> onlineIds = presenceStore.onlineIds(AccountType.USER,
                userAccounts.stream().map(UserAccount::getId).toList());
        List<UserAccountListVO> records = userAccounts.stream()
                .map(account -> toListVO(account, Integer.valueOf(1).equals(account.getStatus())
                        && onlineIds.contains(account.getId())))
                .toList();

        return new PageResult<>(
                total,
                page,
                pageSize,
                records
        );
    }
}
