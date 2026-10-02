import { Group, Stack, Text, Badge, SimpleGrid } from "@mantine/core";
import { Button } from "@app/ui/Button";
import { Icon } from "@app/ui/Icon";
import { useTranslation } from "react-i18next";
import { useEffect, useState } from "react";

const FileThumbnail = ({ file }: { file: File }) => {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    const objectUrl = URL.createObjectURL(file);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [file]);

  if (!url) return <div style={{ height: "100px", marginTop: "8px" }} />;

  return (
    <img
      src={url}
      alt={file.name}
      draggable={false}
      style={{ maxWidth: "100px", height: "100px", objectFit: "contain", marginTop: "8px" }}
    />
  );
};

interface InterleavedPagesSettingsProps {
  files: File[];
  onFilesChange: (files: File[]) => void;
  onRemoveFile: (index: number) => void;
}

export const InterleavedPagesSettings = ({ files, onFilesChange, onRemoveFile }: InterleavedPagesSettingsProps) => {
  const { t } = useTranslation();
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);

  const moveFile = (from: number, to: number) => {
    const newFiles = [...files];
    const [movedFile] = newFiles.splice(from, 1);
    newFiles.splice(to, 0, movedFile);
    onFilesChange(newFiles);
  };

  const moveUp = (index: number) => {
    if (index > 0) moveFile(index, index - 1);
  };

  const moveDown = (index: number) => {
    if (index < files.length - 1) moveFile(index, index + 1);
  };

  const handleDragStart = (index: number) => {
    setDraggedIndex(index);
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
  };

  const handleDrop = (targetIndex: number) => {
    if (draggedIndex !== null && draggedIndex !== targetIndex) {
      moveFile(draggedIndex, targetIndex);
    }
    setDraggedIndex(null);
  };

  return (
    <Stack gap="md">
      <Text size="sm" c="dimmed">
        {t("interleavedPages.settings.reorder", "{{count}} images → {{pages}} pages", {
          count: files.length,
          pages: files.length * 2,
        })}
      </Text>

      <SimpleGrid cols={3} spacing="md">
        {files.map((file, index) => (
          <div
            key={`${file.name}-${index}`}
            draggable
            onDragStart={() => handleDragStart(index)}
            onDragOver={handleDragOver}
            onDrop={() => handleDrop(index)}
            onDragEnd={() => setDraggedIndex(null)}
            style={{
              border: draggedIndex === index ? "2px solid var(--c-primary)" : "1px solid var(--c-border)",
              borderRadius: "8px",
              padding: "12px",
              textAlign: "center",
              cursor: "move",
              opacity: draggedIndex === index ? 0.5 : 1,
            }}
          >
            <Badge>{index + 1}</Badge>
            <FileThumbnail file={file} />
            <Text size="xs" truncate mt="xs">
              {file.name}
            </Text>

            <Group justify="center" mt="xs">
              <Button
                size="sm"
                variant="tertiary"
                accent="neutral"
                onClick={() => moveUp(index)}
                disabled={index === 0}
                aria-label={t("interleavedPages.settings.moveUp", "Move up")}
                leftSection={<Icon name="chevron-up" size="1rem" />}
              />
              <Button
                size="sm"
                variant="tertiary"
                accent="neutral"
                onClick={() => moveDown(index)}
                disabled={index === files.length - 1}
                aria-label={t("interleavedPages.settings.moveDown", "Move down")}
                leftSection={<Icon name="chevron-down" size="1rem" />}
              />
              <Button
                size="sm"
                variant="tertiary"
                accent="danger"
                onClick={() => onRemoveFile(index)}
                aria-label={t("interleavedPages.settings.remove", "Remove file")}
                leftSection={<Icon name="x" size="1rem" />}
              />
            </Group>
          </div>
        ))}
      </SimpleGrid>
    </Stack>
  );
};

export default InterleavedPagesSettings;
