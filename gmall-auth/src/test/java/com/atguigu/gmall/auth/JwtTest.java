package com.atguigu.gmall.auth;

import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {

    // 别忘了创建D:\\project\rsa目录
	private static final String pubKeyPath = "D:\\project-0108\\rsa\\rsa.pub";
    private static final String priKeyPath = "D:\\project-0108\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "123#$@aweDWWE232##");
    }

    @BeforeEach
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 2);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE2MjYyMjgxMzF9.QWsQCML51u25tEmOlLR1Ier14VUJvkmhxhsv7q97d4gi3AMyTluPgharG_Gh6Wdw8FSV9SIt55V3sJQUPhlv12f16i9-Kz_oS0EYjCcpq2pouC451cC_rARl4wK_9r0J2H_8-Qu9m0MoMgwJ5rWE-DRjNUZlM3yysZIvmz7Y-OxtEkPtJyOpdJu3bXYR39f3xQMqNQL5yl5Qm_2v1midjQK55R-x4Dp7icA7rRUxYjrfzj-Gf-nFOnIf1vFh3W6IwTYjxZOqxcq1USkg4HSIxhuFA1_z8XHfn6j5L4_qxVJc-ty3_TqvuSEXz3aFtLxsXubvm2KcNScEufdPc-eTdg";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}