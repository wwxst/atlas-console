package com.web.project.storage.controller;

import com.web.project.common.result.PageResult;
import com.web.project.common.result.Result;
import com.web.project.storage.dto.BindStorageSpaceDTO;
import com.web.project.storage.dto.StorageCredentialsDTO;
import com.web.project.storage.dto.StorageDomainDTO;
import com.web.project.storage.dto.StorageSettingsDTO;
import com.web.project.storage.dto.StorageSpaceQueryDTO;
import com.web.project.storage.service.StorageSettingsService;
import com.web.project.storage.vo.StorageSpaceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sys-user/storage")
@RequiredArgsConstructor
public class StorageController {
    private final StorageSettingsService service;
    public record SettingsVO(String storageType) {}
    public record CredentialsVO(boolean configured) {}
    public record SyncVO(int syncedCount) {}

    @GetMapping("/settings")
    public Result<SettingsVO> settings() { return Result.success(new SettingsVO(service.snapshot().storageType())); }
    @PutMapping("/settings")
    public Result<SettingsVO> updateSettings(@Valid @RequestBody StorageSettingsDTO dto) {
        return Result.success("存储配置已保存", new SettingsVO(service.updateType(dto.storageType()).storageType()));
    }
    @GetMapping("/oss/credentials")
    public Result<CredentialsVO> credentials() { return Result.success(new CredentialsVO(service.snapshot().configured())); }
    @PutMapping("/oss/credentials")
    public Result<CredentialsVO> updateCredentials(@Valid @RequestBody StorageCredentialsDTO dto) {
        service.updateCredentials(dto); return Result.success(new CredentialsVO(true));
    }
    @GetMapping("/spaces")
    public Result<PageResult<StorageSpaceVO>> spaces(@Valid @ModelAttribute StorageSpaceQueryDTO query) {
        return Result.success(service.spaces(query.getPage(), query.getPageSize()));
    }
    @PostMapping("/spaces")
    public Result<StorageSpaceVO> bind(@Valid @RequestBody BindStorageSpaceDTO dto) { return Result.success(service.bind(dto)); }
    @PostMapping("/spaces/sync")
    public Result<SyncVO> sync() { return Result.success(new SyncVO(service.sync())); }
    @PutMapping("/spaces/{id}/default")
    public Result<StorageSpaceVO> setDefault(@PathVariable Long id) { return Result.success(service.setDefault(id)); }
    @PatchMapping("/spaces/{id}/domain")
    public Result<StorageSpaceVO> domain(@PathVariable Long id, @Valid @RequestBody StorageDomainDTO dto) {
        return Result.success(service.updateDomain(id, dto.accessDomain()));
    }
    @DeleteMapping("/spaces/{id}")
    public Result<Void> delete(@PathVariable Long id) { service.deleteSpace(id); return Result.success(); }
}
