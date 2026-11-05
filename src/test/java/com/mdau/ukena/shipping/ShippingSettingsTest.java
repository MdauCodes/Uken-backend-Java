package com.mdau.ukena.shipping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingSettingsTest {

    @Test
    void defaultsChargeFlatFeeBelowThresholdAndFreeFromIt() {
        ShippingSettings settings = ShippingSettings.builder().build();

        assertThat(settings.deliveryChargeFor(262)).isEqualTo(495);
        assertThat(settings.deliveryChargeFor(5999)).isEqualTo(495);
        assertThat(settings.deliveryChargeFor(6000)).isZero();
        assertThat(settings.deliveryChargeFor(13500)).isZero();
    }

    @Test
    void usesAdminValuesWhenSet() {
        ShippingSettings settings = ShippingSettings.builder().build();
        settings.setDeliveryFeePence(399);
        settings.setFreeDeliveryOverPence(4000);

        assertThat(settings.deliveryChargeFor(3999)).isEqualTo(399);
        assertThat(settings.deliveryChargeFor(4000)).isZero();
    }

    @Test
    void weightNoLongerAffectsTheCharge() {
        ShippingSettings settings = ShippingSettings.builder().ratePencePerKg(25000).fallbackWeightGrams(2000).build();

        // The live misconfiguration (£250/kg, 2 kg default) used to price a £2.62 item at £500.
        assertThat(settings.deliveryChargeFor(262)).isEqualTo(495);
    }
}
