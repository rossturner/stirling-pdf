import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MantineProvider } from "@mantine/core";
import InterleavedPagesSettings from "./InterleavedPagesSettings";

vi.mock("react-i18next", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-i18next")>();
  return {
    ...actual,
    useTranslation: () => ({
      t: (_key: string, fallback?: string, values?: Record<string, unknown>) =>
        (fallback ?? _key).replace(/{{(\w+)}}/g, (_, name: string) =>
          String(values?.[name] ?? ""),
        ),
    }),
  };
});

describe("InterleavedPagesSettings", () => {
  const mockFiles = [
    new File(["content1"], "image1.jpg", { type: "image/jpeg" }),
    new File(["content2"], "image2.jpg", { type: "image/jpeg" }),
    new File(["content3"], "image3.jpg", { type: "image/jpeg" }),
  ];

  const renderSettings = (
    files: File[],
    onChange: ReturnType<typeof vi.fn>,
    onRemove: ReturnType<typeof vi.fn>
  ) => {
    return render(
      <MantineProvider>
        <InterleavedPagesSettings
          files={files}
          onFilesChange={onChange}
          onRemoveFile={onRemove}
        />
      </MantineProvider>
    );
  };

  it("renders file list with correct count", () => {
    const onChange = vi.fn();
    const onRemove = vi.fn();

    renderSettings(mockFiles, onChange, onRemove);

    // Should show count summary
    expect(screen.getByText(/3 images → 6 pages/i)).toBeInTheDocument();
  });

  it("calls onRemoveFile when remove button clicked", () => {
    const onChange = vi.fn();
    const onRemove = vi.fn();

    renderSettings(mockFiles, onChange, onRemove);

    const removeButtons = screen.getAllByLabelText("Remove file");
    fireEvent.click(removeButtons[0]);

    expect(onRemove).toHaveBeenCalledWith(0);
  });

  it("calls onFilesChange when moving file up", () => {
    const onChange = vi.fn();
    const onRemove = vi.fn();

    renderSettings(mockFiles, onChange, onRemove);

    const upButtons = screen.getAllByLabelText("Move up");
    // Second file's up button should work
    fireEvent.click(upButtons[1]);

    expect(onChange).toHaveBeenCalled();
    const newFiles = onChange.mock.calls[0][0];
    expect(newFiles[0].name).toBe("image2.jpg");
    expect(newFiles[1].name).toBe("image1.jpg");
  });

  it("disables move up on first file", () => {
    const onChange = vi.fn();
    const onRemove = vi.fn();

    renderSettings(mockFiles, onChange, onRemove);

    const upButtons = screen.getAllByLabelText("Move up");
    expect(upButtons[0]).toBeDisabled();
  });

  it("disables move down on last file", () => {
    const onChange = vi.fn();
    const onRemove = vi.fn();

    renderSettings(mockFiles, onChange, onRemove);

    const downButtons = screen.getAllByLabelText("Move down");
    expect(downButtons[downButtons.length - 1]).toBeDisabled();
  });
});
