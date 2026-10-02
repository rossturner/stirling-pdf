import { BaseParameters } from "@app/types/parameters";
import {
  BaseParametersHook,
  useBaseParameters,
} from "@app/hooks/tools/shared/useBaseParameters";

export interface ColoringBookParameters extends BaseParameters {}

export const defaultParameters: ColoringBookParameters = {};

export type ColoringBookParametersHook =
  BaseParametersHook<ColoringBookParameters>;

export const useColoringBookParameters = (): ColoringBookParametersHook => {
  return useBaseParameters({
    defaultParameters,
    endpointName: "coloring-book",
  });
};
