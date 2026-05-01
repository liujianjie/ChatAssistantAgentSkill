package com.stylemirror.domain.error

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OutcomeTest : StringSpec({

    "Ok exposes value and isOk" {
        val ok: Outcome<Int, String> = Outcome.Ok(7)
        ok.isOk() shouldBe true
        ok.isErr() shouldBe false
        ok.valueOrNull() shouldBe 7
        ok.errorOrNull() shouldBe null
    }

    "Err exposes error and isErr" {
        val err: Outcome<Int, String> = Outcome.Err("boom")
        err.isOk() shouldBe false
        err.isErr() shouldBe true
        err.valueOrNull() shouldBe null
        err.errorOrNull() shouldBe "boom"
    }

    "map transforms Ok value, leaves Err untouched" {
        val ok: Outcome<Int, String> = Outcome.Ok(2)
        ok.map { it * 3 } shouldBe Outcome.Ok(6)

        val err: Outcome<Int, String> = Outcome.Err("nope")
        err.map { it * 3 } shouldBe Outcome.Err("nope")
    }

    "mapError transforms Err, leaves Ok untouched" {
        val err: Outcome<Int, String> = Outcome.Err("low")
        err.mapError { it.uppercase() } shouldBe Outcome.Err("LOW")

        val ok: Outcome<Int, String> = Outcome.Ok(1)
        ok.mapError { it.uppercase() } shouldBe Outcome.Ok(1)
    }

    "flatMap chains Ok, short-circuits on Err" {
        val ok: Outcome<Int, String> = Outcome.Ok(2)
        ok.flatMap { Outcome.Ok(it + 1) } shouldBe Outcome.Ok(3)
        ok.flatMap { Outcome.Err<String>("fail") } shouldBe Outcome.Err("fail")

        val err: Outcome<Int, String> = Outcome.Err("upstream")
        err.flatMap { Outcome.Ok(it + 1) } shouldBe Outcome.Err("upstream")
    }

    "getOrElse returns value or fallback" {
        Outcome.Ok<Int>(5).getOrElse { 99 } shouldBe 5
        (Outcome.Err<String>("x") as Outcome<Int, String>).getOrElse { 99 } shouldBe 99
    }
})
