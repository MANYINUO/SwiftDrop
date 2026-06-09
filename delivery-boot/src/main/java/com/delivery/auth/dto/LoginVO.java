package com.delivery.auth.dto;

import lombok.Data;

/** 登录成功后返回给前端的身份信息与令牌。 */
@Data
public class LoginVO {

    /** 后续请求放在 Authorization: Bearer {token} 里 */
    private String token;

    /** 角色：admin / rider / guest */
    private String role;

    /** 身份主体 ID（管理员 ID / 骑手 ID / 游客用户 ID） */
    private Long id;

    /** 展示名称 */
    private String name;

    /** 骑手编号（仅骑手登录返回） */
    private String riderNo;

    public static LoginVO of(String token, String role, Long id, String name) {
        return of(token, role, id, name, null);
    }

    public static LoginVO of(String token, String role, Long id, String name, String riderNo) {
        LoginVO vo = new LoginVO();
        vo.token = token;
        vo.role = role;
        vo.id = id;
        vo.name = name;
        vo.riderNo = riderNo;
        return vo;
    }
}
