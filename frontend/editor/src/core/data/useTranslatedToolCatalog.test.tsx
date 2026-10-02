import { describe, expect, test, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { useTranslatedToolCatalog } from "@app/data/useTranslatedToolRegistry";
import { SubcategoryId, ToolCategoryId } from "@app/data/toolsTaxonomy";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => fallback ?? key,
    i18n: { changeLanguage: vi.fn(), language: "en-US" },
  }),
  Trans: ({ children }: { children?: unknown }) => children,
}));

describe("translated tool catalog", () => {
  test("useTranslatedToolCatalog_registersColoringBookUnderPageFormatting", () => {
    const { result } = renderHook(() => useTranslatedToolCatalog());

    const coloringBook = result.current.regularTools.coloringBook;

    expect(coloringBook?.categoryId).toBe(ToolCategoryId.STANDARD_TOOLS);
    expect(coloringBook?.subcategoryId).toBe(SubcategoryId.PAGE_FORMATTING);
  });
});
