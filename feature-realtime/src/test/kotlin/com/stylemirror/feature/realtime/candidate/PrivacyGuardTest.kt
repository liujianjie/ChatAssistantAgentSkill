package com.stylemirror.feature.realtime.candidate

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PrivacyGuardTest : StringSpec({

    "phone number is redacted" {
        PrivacyGuard.redact("请拨打13812345678联系") shouldBe "请拨打[REDACTED]联系"
    }

    "national ID is redacted" {
        PrivacyGuard.redact("身份证110101199001011234提交") shouldBe "身份证[REDACTED]提交"
    }

    "plain bank card number is redacted" {
        PrivacyGuard.redact("卡号6222200012345678转账") shouldBe "卡号[REDACTED]转账"
    }

    "grouped bank card is redacted" {
        PrivacyGuard.redact("卡号6222 2000 1234 5678转账") shouldBe "卡号[REDACTED]转账"
    }

    "text without PII is returned unchanged" {
        val safe = "今天天气不错，一起去散步吧😄"
        PrivacyGuard.redact(safe) shouldBe safe
    }

    "multiple PII patterns in one string are all redacted" {
        val text = "电话13812345678，身份证110101199001011234"
        val result = PrivacyGuard.redact(text)
        (result.contains("13812345678")) shouldBe false
        (result.contains("110101199001011234")) shouldBe false
    }

    "short digit sequences that are not PII are left alone" {
        // 5-digit zip code should not be redacted
        PrivacyGuard.redact("邮编100080") shouldBe "邮编100080"
    }
})
