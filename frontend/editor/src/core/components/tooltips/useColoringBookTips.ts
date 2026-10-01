import { useTranslation } from "react-i18next";
import { TooltipContent } from "@app/types/tips";

export const useColoringBookTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("coloringBook.tooltip.header.title", "About coloring books"),
    },
    tips: [
      {
        title: t("coloringBook.tooltip.description.title", "What it does"),
        description: t(
          "coloringBook.tooltip.description.text",
          "Puts each picture on its own page with a blank page after it, so when you print double-sided the back of every picture is empty and colors can't show through.",
        ),
        bullets: [
          t(
            "coloringBook.tooltip.description.bullet1",
            "Pictures go in the same order as your files. Reorder them in the file editor.",
          ),
          t(
            "coloringBook.tooltip.description.bullet2",
            "Every page is A4. Wide pictures get a landscape page.",
          ),
        ],
      },
    ],
  };
};
