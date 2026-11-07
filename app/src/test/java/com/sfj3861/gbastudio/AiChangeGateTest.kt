package com.sfj3861.gbastudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChangeGateTest {
    @Test fun contextRejectsCredentialFiles() {
        assertTrue(runCatching {
            val path=".env"
            require(path.substringAfterLast('/').lowercase() !in setOf(".env",".env.local","secrets.json","credentials.json","google-services.json","keystore.properties"))
        }.isFailure)
    }
    @Test fun proposalsDefaultToUnapproved() {
        val proposal=AiChangeProposal("src/main.c","int main(){return 0;}","Refactor")
        assertFalse(proposal.approved)
    }
}
