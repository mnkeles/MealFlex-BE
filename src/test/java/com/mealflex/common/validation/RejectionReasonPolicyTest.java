package com.mealflex.common.validation;

import com.mealflex.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RejectionReasonPolicyTest {
    @Test
    void rejectsOffensiveWordWithRepeatedLetters() {
        assertThatThrownBy(() -> RejectionReasonPolicy.validateAndNormalize(
                "Üretim limitine geldik ittt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hakaret veya uygunsuz ifade");
    }

    @Test
    void rejectsOffensiveWordsDespiteTurkishCharactersAndCase() {
        assertThatThrownBy(() -> RejectionReasonPolicy.validateAndNormalize("ŞEREFSİZZZ"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("profesyonel");
    }

    @Test
    void acceptsAndCleansProfessionalReason() {
        assertThat(RejectionReasonPolicy.validateAndNormalize(
                "  Günlük üretim kapasitemiz doldu.\nLütfen farklı tarih seçin.  "))
                .isEqualTo("Günlük üretim kapasitemiz doldu. Lütfen farklı tarih seçin.");
    }

    @Test
    void doesNotRejectWordsThatOnlyContainABlockedSequence() {
        assertThat(RejectionReasonPolicy.validateAndNormalize("Malzeme tedarikinde gecikme yaşandı."))
                .isEqualTo("Malzeme tedarikinde gecikme yaşandı.");
    }

    @Test
    void masksPreviouslyStoredOffensiveReasonInCustomerMessage() {
        assertThat(RejectionReasonPolicy.maskInappropriateRejectionReason(
                "Yenimahalle Lezzet Mutfağı talebinizi reddetti. Neden: üretim limitine geldik ittt"))
                .isEqualTo("Yenimahalle Lezzet Mutfağı talebinizi reddetti. Neden: Uygunsuz ifade gizlendi.");
    }
}
