package com.smokerider.app.eta

import org.junit.Assert.assertEquals
import org.junit.Test

object EtaRules {
    fun correct(minutes: Int): Int = when {
        minutes < 5      -> minutes + 11
        minutes in 5..9  -> minutes + 10
        else             -> minutes + 9
    }
    fun adjustOnTheWay(t: Int): Int = when {
        t in 1..9   -> t - 5
        t in 10..15 -> t - 7
        t > 15      -> t - 8
        else        -> t
    }
}

class EtaRulesTest {
    @Test fun correzione_base() {
        assertEquals(14, EtaRules.correct(3))
        assertEquals(17, EtaRules.correct(7))
        assertEquals(24, EtaRules.correct(15))
    }
    @Test fun aggiustamento_in_viaggio() {
        assertEquals(3,  EtaRules.adjustOnTheWay(8))
        assertEquals(5,  EtaRules.adjustOnTheWay(12))
        assertEquals(12, EtaRules.adjustOnTheWay(20))
    }
}
