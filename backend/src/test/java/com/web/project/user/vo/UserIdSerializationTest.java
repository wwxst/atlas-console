package com.web.project.user.vo;

import com.web.project.common.result.PageResult;
import com.web.project.common.result.Result;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserIdSerializationTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void profileIdsRemainExactDecimalStringsInJson() {
        for (long id : new long[]{9007199254740993L, Long.MAX_VALUE, 1L}) {
            UserInfoVO profile = new UserInfoVO(id, "123456", null, null, null, null, 1, null);
            JsonNode jsonId = mapper.readTree(mapper.writeValueAsString(Result.success(profile)))
                    .path("data").path("id");

            assertTrue(jsonId.isString(), "Ordinary user IDs must be JSON strings");
            assertEquals(Long.toString(id), jsonId.asString());
        }
    }

    @Test
    void managedUserIdsRemainExactDecimalStringsInPagedJson() {
        for (long id : new long[]{9007199254740993L, Long.MAX_VALUE, 1L}) {
            UserAccountListVO user = new UserAccountListVO(id, "123456", null, null, null, null, 1, null, null);
            PageResult<UserAccountListVO> page = new PageResult<>(1L, 1, 10, List.of(user));
            JsonNode jsonId = mapper.readTree(mapper.writeValueAsString(Result.success(page)))
                    .path("data").path("records").get(0).path("id");

            assertTrue(jsonId.isString(), "Ordinary user IDs must be JSON strings");
            assertEquals(Long.toString(id), jsonId.asString());
        }
    }

    @Test
    void absentIdsRemainJsonNull() {
        for (Object response : List.of(
                new UserInfoVO(null, "123456", null, null, null, null, 1, null),
                new UserAccountListVO(null, "123456", null, null, null, null, 1, null, null))) {
            JsonNode jsonId = mapper.readTree(mapper.writeValueAsString(response)).path("id");

            assertTrue(jsonId.isNull());
        }
    }
}
