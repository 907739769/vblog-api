package com.seu.blog.controller;

import com.google.code.kaptcha.Producer;
import com.seu.blog.entity.UserEntity;
import com.seu.blog.form.LoginForm;
import com.seu.blog.service.UserService;
import com.seu.blog.service.impl.CaptchaServiceImpl;
import com.seu.blog.service.impl.UserTokenServiceImpl;
import com.seu.common.component.R;
import com.seu.common.constant.Constant;
import com.seu.common.exception.RRException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;


/**
 * 登录相关
 *
 * @author liangfeihu
 * @since 2018/7/4 16:32.
 */
@RestController
public class LoginController {
    @Autowired
    private Producer producer;
    @Resource
    CaptchaServiceImpl captchaService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserTokenServiceImpl userTokenService;

    /**
     * 验证码
     */
    @GetMapping("/captcha")
    public void captcha(HttpServletResponse response, String uuid) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, no-cache");
        response.setContentType("image/jpeg");

        if (StringUtils.isBlank(uuid)) {
            throw new RRException("uuid不能为空");
        }
        //生成文字验证码
        String code = producer.createText();
        captchaService.setCaptcha(uuid, code);

        //获取图片验证码
        BufferedImage image = producer.createImage(code);

        ServletOutputStream out = response.getOutputStream();
        ImageIO.write(image, "jpg", out);
        IOUtils.closeQuietly(out);
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginForm form) throws IOException {
        // 获取Session中验证码
        if (StringUtils.isBlank(form.getUuid())){
            return R.error("参数uuid不能为空");
        }
        String randCode = captchaService.getCaptcha(form.getUuid());
        if (StringUtils.isBlank(randCode)){
            return R.error("图片验证码失效，请重新获取");
        }
        // 用户输入的验证码
        String randomCode = form.getCaptcha();
        if (randomCode == null || !randomCode.equalsIgnoreCase(randCode)) {
            return R.error("图片验证码不正确");
        }

        //用户信息
        UserEntity user = userService.queryByUserAccount(form.getUsername());

        //账号不存在、密码错误
        if (user == null || !user.getPassword().equals(new Sha256Hash(form.getPassword(), user.getSalt()).toHex())) {
            return R.error("账号或密码不正确");
        }

        //账号锁定
        if (Constant.UserStatus.PAUSE.getValue().equals(user.getStatus())) {
            return R.error("账号已被锁定,请联系管理员");
        }

        //生成token，并保存到数据库
        R r = userTokenService.createToken(user.getId());
        return r;
    }




}