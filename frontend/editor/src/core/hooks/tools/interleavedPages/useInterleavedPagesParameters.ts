import { BaseParameters } from "@app/types/parameters";
import {
  useBaseParameters,
  type BaseParametersHook,
} from "@app/hooks/tools/shared/useBaseParameters";

export interface InterleavedPagesParameters extends BaseParameters {
  // No tool-specific parameters; reordering happens in UI
}

export const defaultParameters: InterleavedPagesParameters = {};

export const useInterleavedPagesParameters =
  (): BaseParametersHook<InterleavedPagesParameters> => {
    return useBaseParameters({
      defaultParameters,
      endpointName: "interleaved-pages",
      validateFn: () => true,
    });
  };
