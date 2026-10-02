import { BaseParameters } from "@app/types/parameters";
import {
  useBaseParameters,
  BaseParametersHook,
} from "@app/hooks/tools/shared/useBaseParameters";

export type ColoringBookParameters = BaseParameters;

export const defaultParameters: ColoringBookParameters = {};

export const useColoringBookParameters =
  (): BaseParametersHook<ColoringBookParameters> =>
    useBaseParameters({
      defaultParameters,
      endpointName: "coloring-book",
    });
