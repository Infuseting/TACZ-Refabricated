package fr.infuseting.tacz.durability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GunDurabilityManagerTest {

    @Test
    public void testTarkovThresholdCalculations() {
        float threshold = 90.0f;
        float maxJamChance = 0.25f;

        // Above threshold: 0% jam probability
        float durAbove = 95.0f;
        float probAbove = durAbove >= threshold ? 0.0f : maxJamChance * (float) Math.pow((threshold - durAbove) / threshold, 2);
        assertEquals(0.0f, probAbove);

        // At exactly threshold: 0% jam probability
        float durAt = 90.0f;
        float probAt = durAt >= threshold ? 0.0f : maxJamChance * (float) Math.pow((threshold - durAt) / threshold, 2);
        assertEquals(0.0f, probAt);

        // At 45% (half of threshold): factor = 0.5 -> prob = 0.25 * 0.25 = 0.0625 (6.25%)
        float durHalf = 45.0f;
        float factorHalf = (threshold - durHalf) / threshold;
        float probHalf = maxJamChance * factorHalf * factorHalf;
        assertEquals(0.0625f, probHalf, 0.0001f);

        // At 0%: factor = 1.0 -> prob = 0.25 (25% max jam chance)
        float durZero = 0.0f;
        float factorZero = (threshold - durZero) / threshold;
        float probZero = maxJamChance * factorZero * factorZero;
        assertEquals(0.25f, probZero, 0.0001f);
    }

    @Test
    public void testStatPenalties() {
        float threshold = 90.0f;
        float maxVelLoss = 0.20f;
        float maxDmgLoss = 0.20f;
        float maxSpread = 2.5f;

        // At 90% dura (threshold): 100% velocity, 100% damage, 1.0x spread
        float factor90 = (threshold - 90.0f) / threshold;
        assertEquals(1.0f, 1.0f - factor90 * maxVelLoss);
        assertEquals(1.0f, 1.0f - factor90 * maxDmgLoss);
        assertEquals(1.0f, 1.0f + factor90 * (maxSpread - 1.0f));

        // At 0% dura: 80% velocity, 80% damage, 2.5x spread
        float factor0 = (threshold - 0.0f) / threshold;
        assertEquals(0.80f, 1.0f - factor0 * maxVelLoss, 0.0001f);
        assertEquals(0.80f, 1.0f - factor0 * maxDmgLoss, 0.0001f);
        assertEquals(2.5f, 1.0f + factor0 * (maxSpread - 1.0f), 0.0001f);
    }

    @Test
    public void testPunitiveMaxCapDegradation() {
        float regularBaseLoss = 2.5f;
        float heavyBaseLoss = 8.0f;
        float restoreFactor = 0.10f;

        // Case 1: Cleaning at 85% durability (15% restored) -> regular loss
        float current1 = 85.0f;
        float maxCap1 = 100.0f;
        float restored1 = maxCap1 - current1;
        float totalCapLoss1 = regularBaseLoss + (restored1 * restoreFactor);
        assertEquals(4.0f, totalCapLoss1, 0.0001f); // 2.5 + 1.5 = 4.0%
        assertEquals(96.0f, maxCap1 - totalCapLoss1, 0.0001f);

        // Case 2: Cleaning at 50% durability (50% restored) -> interpolated base loss
        float current2 = 50.0f;
        float maxCap2 = 100.0f;
        float restored2 = maxCap2 - current2;
        float t = (80.0f - current2) / 40.0f; // 30/40 = 0.75
        float baseLoss2 = regularBaseLoss + t * (heavyBaseLoss - regularBaseLoss); // 2.5 + 0.75 * 5.5 = 6.625
        float totalCapLoss2 = baseLoss2 + (restored2 * restoreFactor); // 6.625 + 5.0 = 11.625%
        assertEquals(11.625f, totalCapLoss2, 0.0001f);
        assertEquals(88.375f, maxCap2 - totalCapLoss2, 0.0001f);

        // Case 3: Cleaning at 20% durability (80% restored) -> heavy loss
        float current3 = 20.0f;
        float maxCap3 = 100.0f;
        float restored3 = maxCap3 - current3;
        float totalCapLoss3 = heavyBaseLoss + (restored3 * restoreFactor); // 8.0 + 8.0 = 16.0%
        assertEquals(16.0f, totalCapLoss3, 0.0001f);
        assertEquals(84.0f, maxCap3 - totalCapLoss3, 0.0001f);
    }
}
