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

export const BROWSE_CATEGORIES: BrowseCategory[] = [
  {
    id: "made-for-you",
    title: "Made For You",
    searchQuery: "Top Hits 2025",
    gradient: "from-purple-600 via-purple-800 to-indigo-950",
    icon: Disc3,
    badgeColor: "bg-purple-500",
  },
  {
    id: "new-releases",
    title: "New Releases",
    searchQuery: "New Music Friday",
    gradient: "from-emerald-600 via-teal-800 to-emerald-950",
    icon: Radio,
    badgeColor: "bg-emerald-500",
  },
  {
    id: "pop",
    title: "Pop Hits",
    searchQuery: "Pop Music",
    gradient: "from-blue-600 via-indigo-800 to-slate-950",
    icon: Flame,
    badgeColor: "bg-blue-500",
  },
  {
    id: "hip-hop",
    title: "Hip-Hop & Rap",
    searchQuery: "Hip Hop Rap",
    gradient: "from-amber-600 via-orange-800 to-stone-950",
    icon: Headphones,
    badgeColor: "bg-amber-500",
  },
  {
    id: "rock",
    title: "Rock Classics",
    searchQuery: "Rock Classics",
    gradient: "from-rose-600 via-red-800 to-neutral-950",
    icon: Guitar,
    badgeColor: "bg-rose-500",
  },
  {
    id: "lofi",
    title: "Lo-Fi & Chill",
    searchQuery: "Lofi Hip Hop Chill Beats",
    gradient: "from-teal-600 via-cyan-800 to-slate-950",
    icon: Coffee,
    badgeColor: "bg-teal-500",
  },
  {
    id: "electronic",
    title: "Dance & EDM",
    searchQuery: "Electronic Dance Music",
    gradient: "from-fuchsia-600 via-pink-800 to-purple-950",
    icon: Sparkles,
    badgeColor: "bg-fuchsia-500",
  },
  {
    id: "acoustic",
    title: "Acoustic & Folk",
    searchQuery: "Acoustic Pop Indie",
    gradient: "from-lime-600 via-emerald-800 to-zinc-950",
    icon: Music,
    badgeColor: "bg-lime-500",
  },
  {
    id: "rnb",
    title: "R&B & Soul",
    searchQuery: "R&B Soul",
    gradient: "from-violet-600 via-purple-900 to-neutral-950",
    icon: Heart,
    badgeColor: "bg-violet-500",
  },
  {
    id: "gaming",
    title: "Gaming & Synth",
    searchQuery: "Synthwave Cyberpunk",
    gradient: "from-cyan-600 via-blue-900 to-black",
    icon: Gamepad2,
    badgeColor: "bg-cyan-500",
  },
  {
    id: "jazz",
    title: "Jazz & Blues",
    searchQuery: "Smooth Jazz Blues",
    gradient: "from-yellow-600 via-amber-800 to-stone-950",
    icon: Volume2,
    badgeColor: "bg-yellow-500",
  },
  {
    id: "ambient",
    title: "Focus & Ambient",
    searchQuery: "Deep Focus Ambient Sleep",
    gradient: "from-slate-600 via-gray-800 to-zinc-950",
    icon: Moon,
    badgeColor: "bg-slate-500",
  },
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
      className={`group relative overflow-hidden rounded-2xl p-4 sm:p-5 h-28 sm:h-36 cursor-pointer bg-linear-to-br ${category.gradient} transition-all duration-300 hover:scale-[1.02] active:scale-95 shadow-md hover:shadow-xl hover:shadow-black/40 border border-white/10 flex flex-col justify-between select-none`}
    >
      {/* Title */}
      <h3 className="font-heading font-bold text-base sm:text-xl text-white tracking-tight leading-snug drop-shadow-xs max-w-[70%] z-10">
        {category.title}
      </h3>

      {/* Decorative Rotated Visual Badge in Bottom-Right (Figma / Spotify Style) */}
      <div className="absolute -bottom-2 -right-2 sm:-bottom-3 sm:-right-3 size-16 sm:size-20 rounded-xl bg-black/30 backdrop-blur-xs flex items-center justify-center rotate-14 group-hover:rotate-18 transition-transform duration-300 shadow-xl border border-white/15 overflow-hidden">
        <div className={`size-full flex items-center justify-center ${category.badgeColor}`}>
          <Icon className="size-8 sm:size-10 text-white/90 drop-shadow-md" />
        </div>
      </div>
    </div>
  );
};

export default BrowseCategoryCard;
