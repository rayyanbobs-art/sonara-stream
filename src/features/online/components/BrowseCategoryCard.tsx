import {
  LucideIcon,
  Flame,
  Radio,
  Disc3,
  Headphones,
  Guitar,
  Coffee,
  Sparkles,
  Heart,
  Gamepad2,
  Music,
  Volume2,
  Moon,
} from "lucide-react";

export type BrowseCategory = {
  id: string;
  title: string;
  searchQuery: string;
  gradient: string;
  icon: LucideIcon;
  badgeColor: string;
};

export const DISCOVER_CATEGORIES: BrowseCategory[] = [
  {
    id: "made-for-you",
    title: "Made For You",
    searchQuery: "Top Hits 2025",
    gradient: "from-blue-900 via-indigo-900 to-slate-950",
    icon: Disc3,
    badgeColor: "bg-blue-600",
  },
  {
    id: "new-releases",
    title: "New Releases",
    searchQuery: "New Music Friday",
    gradient: "from-amber-600 via-orange-700 to-amber-950",
    icon: Radio,
    badgeColor: "bg-amber-500",
  },
  {
    id: "classics",
    title: "Classics",
    searchQuery: "Classic Hits Greatest Songs",
    gradient: "from-blue-700 via-cyan-900 to-slate-950",
    icon: Music,
    badgeColor: "bg-cyan-600",
  },
  {
    id: "charts",
    title: "Charts",
    searchQuery: "Top 50 Global",
    gradient: "from-purple-600 via-purple-800 to-indigo-950",
    icon: Flame,
    badgeColor: "bg-purple-500",
  },
  {
    id: "trending",
    title: "Trending",
    searchQuery: "Viral Hits Trending Music",
    gradient: "from-fuchsia-600 via-pink-700 to-rose-950",
    icon: Sparkles,
    badgeColor: "bg-fuchsia-500",
  },
  {
    id: "discover",
    title: "Discover",
    searchQuery: "Discover Weekly Indie Gems",
    gradient: "from-violet-500 via-purple-700 to-slate-900",
    icon: Headphones,
    badgeColor: "bg-violet-500",
  },
  {
    id: "live-sessions",
    title: "Live & Singles",
    searchQuery: "Acoustic Live Studio Session",
    gradient: "from-zinc-600 via-neutral-800 to-neutral-950",
    icon: Volume2,
    badgeColor: "bg-zinc-500",
  },
  {
    id: "decades",
    title: "Decades",
    searchQuery: "80s 90s 2000s Hits",
    gradient: "from-amber-700 via-orange-800 to-stone-950",
    icon: Coffee,
    badgeColor: "bg-orange-500",
  },
];

export const GENRE_CATEGORIES: BrowseCategory[] = [
  {
    id: "pop",
    title: "Pop",
    searchQuery: "Pop Music",
    gradient: "from-emerald-600 via-green-700 to-teal-950",
    icon: Flame,
    badgeColor: "bg-emerald-500",
  },
  {
    id: "country",
    title: "Country",
    searchQuery: "Country Hits",
    gradient: "from-orange-600 via-amber-700 to-stone-950",
    icon: Guitar,
    badgeColor: "bg-amber-600",
  },
  {
    id: "hip-hop",
    title: "Hip-Hop",
    searchQuery: "Hip Hop Rap",
    gradient: "from-slate-700 via-indigo-950 to-neutral-950",
    icon: Headphones,
    badgeColor: "bg-slate-500",
  },
  {
    id: "rock",
    title: "Rock",
    searchQuery: "Rock Classics Modern Rock",
    gradient: "from-teal-700 via-cyan-950 to-slate-950",
    icon: Guitar,
    badgeColor: "bg-teal-500",
  },
  {
    id: "indie",
    title: "Indie",
    searchQuery: "Indie Alternative",
    gradient: "from-rose-600 via-red-700 to-stone-950",
    icon: Heart,
    badgeColor: "bg-rose-500",
  },
  {
    id: "punk",
    title: "Punk",
    searchQuery: "Punk Rock Pop Punk",
    gradient: "from-blue-800 via-indigo-950 to-neutral-950",
    icon: Gamepad2,
    badgeColor: "bg-blue-600",
  },
  {
    id: "metal",
    title: "Metal",
    searchQuery: "Heavy Metal Hard Rock",
    gradient: "from-red-700 via-rose-900 to-black",
    icon: Flame,
    badgeColor: "bg-red-600",
  },
  {
    id: "instrumental",
    title: "Instrumental",
    searchQuery: "Instrumental Acoustic Ambient",
    gradient: "from-sky-700 via-blue-900 to-slate-950",
    icon: Moon,
    badgeColor: "bg-sky-500",
  },
];

export const BROWSE_CATEGORIES: BrowseCategory[] = [
  ...DISCOVER_CATEGORIES,
  ...GENRE_CATEGORIES,
];

type BrowseCategoryCardProps = {
  category: BrowseCategory;
  onClick: (query: string) => void;
};

export const BrowseCategoryCard = ({
  category,
  onClick,
}: BrowseCategoryCardProps) => {
  const Icon = category.icon;

  return (
    <div
      onClick={() => onClick(category.searchQuery)}
      className={`group relative overflow-hidden rounded-2xl p-3.5 sm:p-4 h-24 sm:h-28 md:h-32 xl:h-36 cursor-pointer bg-linear-to-br ${category.gradient} transition-all duration-300 hover:scale-[1.02] active:scale-95 shadow-md hover:shadow-xl hover:shadow-black/40 border border-white/10 flex flex-col justify-between select-none`}
    >
      {/* Title */}
      <h3 className="font-heading font-bold text-sm sm:text-base md:text-lg text-white tracking-tight leading-snug drop-shadow-xs max-w-[75%] z-10">
        {category.title}
      </h3>

      {/* Decorative Rotated Visual Badge in Bottom-Right (Figma / Spotify Style) */}
      <div className="absolute -bottom-2 -right-2 sm:-bottom-3 sm:-right-3 size-14 sm:size-16 md:size-20 rounded-xl bg-black/30 backdrop-blur-xs flex items-center justify-center rotate-14 group-hover:rotate-18 transition-transform duration-300 shadow-xl border border-white/15 overflow-hidden">
        <div className={`size-full flex items-center justify-center ${category.badgeColor}`}>
          <Icon className="size-6 sm:size-8 md:size-10 text-white/90 drop-shadow-md" />
        </div>
      </div>
    </div>
  );
};

export default BrowseCategoryCard;
