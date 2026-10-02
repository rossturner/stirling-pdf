import { describe, expect, test } from "vitest";
import { URL_TO_TOOL_MAP } from "@app/utils/urlMapping";

describe("URL_TO_TOOL_MAP", () => {
  test("maps the coloring book URL to the coloringBook tool", () => {
    expect(URL_TO_TOOL_MAP["/coloring-book"]).toBe("coloringBook");
  });
});
