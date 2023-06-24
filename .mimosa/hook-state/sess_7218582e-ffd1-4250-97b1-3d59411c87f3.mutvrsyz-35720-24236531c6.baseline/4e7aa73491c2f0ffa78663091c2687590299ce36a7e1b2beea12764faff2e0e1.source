package com.examforge.trade.controller;

import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.trade.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** 支付渠道接口（docs/14 O-4/D14；网关 /api/v1/payments/**；双回调端点走网关白名单，验签为唯一安全边界） */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 渠道下单（登录+本人+CREATED）：{returnUrl?} → 微信 codeUrl（渲染二维码）/ 支付宝 payUrl（跳转） */
    @PostMapping("/{orderNo}/prepay")
    public Result<Map<String, Object>> prepay(@RequestHeader("X-User-Id") String uid,
                                              @PathVariable String orderNo,
                                              @RequestBody(required = false) Map<String, String> body) {
        return Result.ok(paymentService.prepay(Long.valueOf(uid), orderNo,
                body == null ? null : body.get("returnUrl")));
    }

    /** 微信支付 V3 回调（渠道调用，验签+解密在 provider；header 透传给 NotificationParser） */
    @PostMapping("/wechat/notify")
    public Result<Map<String, Object>> wechatNotify(HttpServletRequest request,
                                                    @RequestBody String body) {
        return Result.ok(paymentService.handleCallback("WECHAT", headerMap(request), body, Map.of()));
    }

    /** 支付宝异步通知（渠道 form 表单，RSA2 验签在 provider） */
    @PostMapping("/alipay/notify")
    public Result<Map<String, Object>> alipayNotify(HttpServletRequest request,
                                                    @RequestParam Map<String, String> form) {
        return Result.ok(paymentService.handleCallback("ALIPAY", Map.of(), null, form));
    }

    /** 支付状态（登录+本人）：本地状态 + 渠道查单兜底（回调丢失时补履约） */
    @GetMapping("/{orderNo}/status")
    public Result<Map<String, Object>> status(@RequestHeader("X-User-Id") String uid,
                                              @PathVariable String orderNo) {
        return Result.ok(paymentService.status(Long.valueOf(uid), orderNo));
    }

    private Map<String, String> headerMap(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        for (String h : new String[]{"Wechatpay-Serial", "Wechatpay-Nonce", "Wechatpay-Signature",
                "Wechatpay-Timestamp", "Request-ID"}) {
            headers.put(h, request.getHeader(h));
        }
        return headers;
    }
}
