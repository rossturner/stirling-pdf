import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useInterleavedPagesTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("interleavedPages.tooltip.header.title", "Interleaved Pages"),
    },
    tips: [
      {
        title: t(
          "interleavedPages.tooltip.description.title",
          "What does this tool do?"
        ),
        description: t(
          "interleavedPages.tooltip.description.text",
          "Combines multiple images into a single PDF with blank pages on the back of each image, perfect for duplex printing and creating coloring books."
        ),
        bullets: [
          t(
            "interleavedPages.tooltip.description.bullet1",
            "Perfect for coloring books and print-on-demand projects"
          ),
          t(
            "interleavedPages.tooltip.description.bullet2",
            "Save paper by printing on both sides"
          ),
        ],
      },
    ],
  };
};
