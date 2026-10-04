import tokensData from "./tokens.json";

export interface ColorPalette {
  parchment: string;
  lakeBlue: string;
  periwinkleMist: string;
  skyBlue: string;
  mint: string;
  coral: string;
  gold: string;
  crimson: string;
  offBlack: string;
  ink: string;
  graphite: string;
  smoke: string;
  ash: string;
  surface?: string;
}

export interface TypographyToken {
  size: string;
  lineHeight: number;
  letterSpacing: string;
}

export const VOX_TOKENS = tokensData;

export const VOX_COLORS = {
  light: tokensData.color.light as ColorPalette,
  dark: tokensData.color.dark as ColorPalette,
};

export const VOX_TYPOGRAPHY = tokensData.typography;
export const VOX_SPACING = tokensData.spacing;
export const VOX_RADIUS = tokensData.radius;
export const VOX_LAYOUT = tokensData.layout;

export type ThemeMode = "light" | "dark";

export function getVoxColor(
  name: keyof ColorPalette,
  mode: ThemeMode = "light"
): string {
  const palette = VOX_COLORS[mode];
  const color = palette[name] ?? VOX_COLORS.light[name];
  return color ?? "#000000";
}
