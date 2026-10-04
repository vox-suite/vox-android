import * as React from "react";
import { ArrowRight, ChevronDown, X, Moon, Sun } from "lucide-react";
import { cn } from "../../lib/utils";

export interface AnnouncementBarProps extends React.HTMLAttributes<HTMLDivElement> {
  badge?: string;
  message: string;
  actionText?: string;
  actionHref?: string;
  onAction?: () => void;
  onClose?: () => void;
}

export function AnnouncementBar({
  badge,
  message,
  actionText,
  actionHref,
  onAction,
  onClose,
  className,
  ...props
}: AnnouncementBarProps) {
  const [visible, setVisible] = React.useState(true);

  if (!visible) return null;

  const handleClose = () => {
    setVisible(false);
    onClose?.();
  };

  return (
    <aside
      role="banner"
      className={cn(
        "relative z-50 flex min-h-[40px] w-full items-center justify-between border-b border-black/10 bg-black px-4 py-2 font-mono text-[13px] text-[#f6f3f1] sm:px-6 sm:text-[14px]",
        className
      )}
      {...props}
    >
      <div className="mx-auto flex flex-wrap items-center justify-center gap-2 sm:gap-3 text-center">
        {badge && (
          <span className="rounded-full border border-white/30 bg-white/10 px-2 py-0.5 text-[11px] font-medium tracking-wider text-white uppercase">
            {badge}
          </span>
        )}
        <span className="tracking-[-0.02em] font-normal">{message}</span>
        {actionText && (
          actionHref ? (
            <a
              href={actionHref}
              className="ml-2 inline-flex items-center rounded-full border border-white bg-white px-3 py-0.5 text-[11px] font-medium tracking-wider text-black uppercase transition-transform hover:scale-105 active:scale-95"
            >
              {actionText}
            </a>
          ) : (
            <button
              type="button"
              onClick={onAction}
              className="ml-2 inline-flex items-center rounded-full border border-white bg-white px-3 py-0.5 text-[11px] font-medium tracking-wider text-black uppercase transition-transform hover:scale-105 active:scale-95"
            >
              {actionText}
            </button>
          )
        )}
      </div>

      <button
        type="button"
        onClick={handleClose}
        aria-label="Dismiss announcement"
        className="ml-2 flex size-6 shrink-0 items-center justify-center rounded-full text-white/70 hover:text-white transition-colors"
      >
        <X className="size-3.5" />
      </button>
    </aside>
  );
}

export interface PillButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "lake" | "black" | "ghost" | "link";
  size?: "default" | "sm" | "lg";
  asChild?: boolean;
}

export const PillButton = React.forwardRef<HTMLButtonElement, PillButtonProps>(
  ({ className, variant = "lake", size = "default", children, ...props }, ref) => {
    const baseClasses =
      "inline-flex shrink-0 items-center justify-center font-mono font-medium uppercase transition-all duration-150 select-none outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50";

    const variantClasses = {
      lake: "bg-[#2b59d1] dark:bg-[#3d6cf0] text-white hover:brightness-110 shadow-sm active:translate-y-px rounded-[100px]",
      black: "bg-[#242424] dark:bg-[#f6f3f1] text-[#f6f3f1] dark:text-[#242424] hover:bg-black dark:hover:bg-white active:translate-y-px rounded-[100px]",
      ghost: "bg-transparent border border-[#cecac8] dark:border-[#2e2c29] text-[#242424] dark:text-[#f6f3f1] hover:bg-[#cecac8]/20 dark:hover:bg-white/5 active:translate-y-px rounded-[100px]",
      link: "bg-transparent text-[#242424] dark:text-[#f6f3f1] hover:underline underline-offset-4 tracking-wider p-0",
    };

    const sizeClasses = {
      sm: "h-9 px-5 text-[12px] tracking-[-0.033em] gap-1.5",
      default: "h-12 px-8 text-[14px] tracking-[-0.02em] gap-2",
      lg: "h-14 px-10 text-[16px] tracking-[-0.025em] gap-2.5",
    };

    return (
      <button
        ref={ref}
        className={cn(
          baseClasses,
          variantClasses[variant],
          variant !== "link" && sizeClasses[size],
          className
        )}
        {...props}
      >
        {children}
        {variant === "lake" && (
          <span className="ml-1 inline-block transition-transform group-hover:translate-x-0.5" aria-hidden="true">
            ▸
          </span>
        )}
        {variant === "link" && (
          <ArrowRight className="ml-1 size-3.5 inline-block" aria-hidden="true" />
        )}
      </button>
    );
  }
);
PillButton.displayName = "PillButton";

export interface PipelineNodeTagProps extends React.HTMLAttributes<HTMLDivElement> {
  icon?: React.ReactNode;
  label: string;
  active?: boolean;
}

export function PipelineNodeTag({
  icon,
  label,
  active = false,
  className,
  ...props
}: PipelineNodeTagProps) {
  return (
    <div
      className={cn(
        "inline-flex items-center gap-2.5 rounded-full border px-5 py-3 font-mono text-[14px] font-medium uppercase tracking-[-0.02em] transition-all",
        active
          ? "border-[#2b59d1] bg-[#cfdaf5]/30 text-[#2b59d1] dark:border-[#3d6cf0] dark:bg-[#3d6cf0]/10 dark:text-[#7ba2ff]"
          : "border-[#cecac8] bg-[#f6f3f1] text-[#242424] dark:border-[#2e2c29] dark:bg-[#1c1b19] dark:text-[#f6f3f1]",
        className
      )}
      {...props}
    >
      {icon && <span className="size-4 shrink-0">{icon}</span>}
      <span>{label}</span>
    </div>
  );
}

export interface FeatureCardProps extends React.HTMLAttributes<HTMLDivElement> {
  icon?: React.ReactNode;
  title: string;
  description: string;
}

export function FeatureCard({
  icon,
  title,
  description,
  className,
  children,
  ...props
}: FeatureCardProps) {
  return (
    <div
      className={cn(
        "relative flex flex-col justify-between overflow-hidden rounded-[40px] border border-[#cecac8] dark:border-[#2c2a27] bg-[#f6f3f1] dark:bg-[#1c1b19] p-8 sm:p-10 transition-colors",
        className
      )}
      {...props}
    >
      <div>
        {icon && (
          <div className="mb-6 inline-flex size-10 items-center justify-center text-[#242424] dark:text-[#f6f3f1]">
            {icon}
          </div>
        )}
        <h3 className="font-serif text-[24px] font-normal leading-[1.2] tracking-[-0.02em] text-[#242424] dark:text-[#f6f3f1]">
          {title}
        </h3>
        <p className="mt-4 font-mono text-[16px] font-normal leading-[1.35] tracking-[-0.025em] text-[#4e4d4d] dark:text-[#aba7a2]">
          {description}
        </p>
      </div>
      {children && <div className="mt-6">{children}</div>}
    </div>
  );
}

export interface ElevatedFeatureCardProps extends React.HTMLAttributes<HTMLDivElement> {
  title: string;
  description: string;
  tag?: string;
  graphic?: React.ReactNode;
}

export function ElevatedFeatureCard({
  title,
  description,
  tag,
  graphic,
  className,
  children,
  ...props
}: ElevatedFeatureCardProps) {
  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-[40px] border border-[#a0b5eb]/50 dark:border-[#3d6cf0]/30 bg-[#cfdaf5] dark:bg-[#1c2438] p-8 sm:p-10 text-[#242424] dark:text-[#f6f3f1] transition-all",
        className
      )}
      {...props}
    >
      <div
        className="pointer-events-none absolute -right-16 -top-16 size-80 rounded-full bg-gradient-to-br from-[#ff9473]/30 via-[#a0b5eb]/40 to-[#a7fccd]/30 blur-3xl dark:from-[#ff9473]/15 dark:via-[#3d6cf0]/25 dark:to-[#a7fccd]/15"
        aria-hidden="true"
      />

      <div className="relative z-10 grid gap-8 lg:grid-cols-2 lg:items-center">
        <div>
          {tag && (
            <span className="mb-4 inline-block font-mono text-[12px] font-medium uppercase tracking-wider text-[#2b59d1] dark:text-[#7ba2ff]">
              {tag}
            </span>
          )}
          <h3 className="font-serif text-[28px] sm:text-[32px] font-normal leading-[1.2] tracking-[-0.02em] text-[#242424] dark:text-[#f6f3f1]">
            {title}
          </h3>
          <p className="mt-4 font-mono text-[16px] font-normal leading-[1.35] tracking-[-0.025em] text-[#4e4d4d] dark:text-[#cfdaf5]/80">
            {description}
          </p>
          {children && <div className="mt-6">{children}</div>}
        </div>

        {graphic && (
          <div className="relative flex items-center justify-center overflow-hidden rounded-2xl bg-[#f6f3f1]/40 dark:bg-black/20 p-6 backdrop-blur-sm border border-white/20 dark:border-white/5">
            {graphic}
          </div>
        )}
      </div>
    </div>
  );
}

export interface FAQAccordionItemProps {
  question: string;
  answer: string;
  defaultOpen?: boolean;
}

export function FAQAccordionItem({
  question,
  answer,
  defaultOpen = false,
}: FAQAccordionItemProps) {
  const [isOpen, setIsOpen] = React.useState(defaultOpen);

  return (
    <div className="border-b border-[#cecac8] dark:border-[#2c2a27] py-8 sm:py-10 transition-colors">
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        aria-expanded={isOpen}
        className="flex w-full items-center justify-between text-left group"
      >
        <span className="font-serif text-[22px] sm:text-[24px] font-normal leading-[1.2] tracking-[-0.02em] text-[#242424] dark:text-[#f6f3f1] group-hover:text-[#2b59d1] dark:group-hover:text-[#7ba2ff] transition-colors">
          {question}
        </span>
        <ChevronDown
          className={cn(
            "size-5 shrink-0 text-[#242424] dark:text-[#f6f3f1] transition-transform duration-200",
            isOpen && "rotate-180"
          )}
          aria-hidden="true"
        />
      </button>
      {isOpen && (
        <div className="mt-4 pr-12 font-mono text-[16px] font-normal leading-[1.35] tracking-[-0.025em] text-[#4e4d4d] dark:text-[#aba7a2] animate-in fade-in-50 duration-200">
          {answer}
        </div>
      )}
    </div>
  );
}

export interface AtmosphericWashProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: "coral-sky" | "sky-mint" | "gold-coral";
}

export function AtmosphericWash({
  variant = "coral-sky",
  className,
  ...props
}: AtmosphericWashProps) {
  const gradients = {
    "coral-sky": "from-[#ff9473]/30 via-[#a0b5eb]/25 to-transparent dark:from-[#ff9473]/15 dark:via-[#3d6cf0]/15 dark:to-transparent",
    "sky-mint": "from-[#a0b5eb]/30 via-[#a7fccd]/25 to-transparent dark:from-[#3d6cf0]/15 dark:via-[#a7fccd]/15 dark:to-transparent",
    "gold-coral": "from-[#ecda98]/30 via-[#ff9473]/25 to-transparent dark:from-[#ecda98]/15 dark:via-[#ff9473]/15 dark:to-transparent",
  };

  return (
    <div
      aria-hidden="true"
      className={cn(
        "pointer-events-none absolute rounded-full bg-gradient-to-r blur-[75px]",
        gradients[variant],
        className
      )}
      {...props}
    />
  );
}

export function VoxThemeToggle({ className }: { className?: string }) {
  const [theme, setTheme] = React.useState<"light" | "dark">("light");

  React.useEffect(() => {
    const isDark = document.documentElement.classList.contains("dark");
    setTheme(isDark ? "dark" : "light");
  }, []);

  const toggleTheme = () => {
    const nextTheme = theme === "light" ? "dark" : "light";
    setTheme(nextTheme);
    if (nextTheme === "dark") {
      document.documentElement.classList.add("dark");
      localStorage.setItem("vox_theme", "dark");
    } else {
      document.documentElement.classList.remove("dark");
      localStorage.setItem("vox_theme", "light");
    }
  };

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={`Switch to ${theme === "light" ? "dark" : "light"} mode`}
      className={cn(
        "inline-flex h-8 items-center gap-1.5 rounded-full border border-[#cecac8] dark:border-[#2c2a27] bg-[#f6f3f1] dark:bg-[#1c1b19] px-2.5 font-mono text-[11px] uppercase tracking-wider text-[#4e4d4d] dark:text-[#aba7a2] hover:border-[#242424] dark:hover:border-[#f6f3f1] transition-all",
        className
      )}
    >
      {theme === "light" ? (
        <>
          <Sun className="size-3.5 text-[#f37a0a]" />
          <span>Light</span>
        </>
      ) : (
        <>
          <Moon className="size-3.5 text-[#3d6cf0]" />
          <span>Dark</span>
        </>
      )}
    </button>
  );
}
