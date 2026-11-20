package com.stockvision.portfolio.domain;

/**
 * Configurable brokerage fee and withholding tax policy on a virtual portfolio.
 */
public enum FeeTaxPolicy {
    EXCLUDED_MVP(
            "EXCLUDED_MVP politikası kapsamında komisyon ve işlem vergileri hariç tutulur; "
                    + "işlem girişinde sıfırdan büyük komisyon/vergi kabul edilmez."),
    EXPLICIT_PER_TXN(
            "İşlem başına açıkça girilen komisyon ve vergi tutarları alış (BUY) maliyet tabanına eklenir, "
                    + "satış (SELL) net hasılatından düşülür.");

    private final String description;

    FeeTaxPolicy(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
