import { BaseParameters } from "@app/types/parameters";
import {
  BaseParametersHook,
  useBaseParameters,
} from "@app/hooks/tools/shared/useBaseParameters";

export type ColoringBookParameters = BaseParameters;

export const defaultParameters: ColoringBookParameters = {};

export type ColoringBookParametersHook =
  BaseParametersHook<ColoringBookParameters>;

export const useColoringBookParameters = (): ColoringBookParametersHook =>
  useBaseParameters({
    defaultParameters,
    endpointName: "coloring-book",
  });
