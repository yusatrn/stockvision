import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { SymbolSelect } from "./symbol-select";

const listMock = vi.fn();

vi.mock("@/lib/api/services", () => ({
  apiServices: {
    instruments: {
      list: (...args: unknown[]) => listMock(...args),
    },
  },
}));

describe("SymbolSelect", () => {
  it("loads the catalog into a list, pins holdings, filters, and emits the chosen symbol", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    listMock.mockResolvedValue({
      items: [
        { id: "1", symbol: "THYAO", name: "Türk Hava Yolları", exchange: "BIST", sector: null, currency: "TRY", type: "EQUITY", active: true },
        { id: "2", symbol: "PGSUS", name: "Pegasus", exchange: "BIST", sector: null, currency: "TRY", type: "EQUITY", active: true },
        { id: "3", symbol: "SASA", name: "Sasa Polyester", exchange: "BIST", sector: null, currency: "TRY", type: "EQUITY", active: true },
      ],
      page: 0,
      size: 100,
      totalElements: 3,
      totalPages: 1,
      dataSource: {},
    });

    render(
      <SymbolSelect
        id="sym"
        value="THYAO"
        onChange={onChange}
        pinnedSymbols={["SASA"]}
      />
    );

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "PGSUS — Pegasus" })).toBeInTheDocument();
    });
    expect(screen.getByRole("group", { name: "Portföydeki Hisseler" })).toBeInTheDocument();

    await user.type(screen.getByLabelText("Sembol listesinde ara"), "pegasus");
    expect(screen.queryByRole("option", { name: /SASA/ })).not.toBeInTheDocument();

    await user.selectOptions(screen.getByRole("combobox"), "PGSUS");
    expect(onChange).toHaveBeenCalledWith("PGSUS");
  });
});
