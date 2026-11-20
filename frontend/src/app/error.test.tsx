import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import GlobalErrorPage from "./error";

describe("GlobalErrorPage", () => {
  it("renders safe error message, digest reference, and invokes reset callback", async () => {
    const user = userEvent.setup();
    const resetMock = vi.fn();
    const err = Object.assign(new Error("Internal stack detail omitted"), {
      digest: "digest-789",
    });

    render(<GlobalErrorPage error={err} reset={resetMock} />);

    expect(screen.getByText("Beklenmeyen Bir Hata Oluştu")).toBeInTheDocument();
    expect(screen.getByText(/Referans Kimliği: digest-789/)).toBeInTheDocument();
    expect(
      screen.queryByText(/Internal stack detail omitted/)
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Tekrar Dene" }));
    expect(resetMock).toHaveBeenCalledTimes(1);
  });
});
