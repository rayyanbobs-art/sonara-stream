import { Link } from "@tanstack/react-router";
import { Home, Search, Library, Heart, User } from "lucide-react";

export const BottomNavBar = () => {
  const navItems = [
    { name: "Home", href: "/", icon: Home },
    { name: "Search", href: "/stream", icon: Search },
    { name: "Library", href: "/songs", icon: Library },
    { name: "Favorites", href: "/favorites", icon: Heart },
    { name: "Me", href: "/settings", icon: User },
  ];

  return (
    <nav className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-[#121212]/95 backdrop-blur-2xl border-t border-white/5 px-2 pt-1.5 pb-[max(0.375rem,env(safe-area-inset-bottom))]">
      <div className="flex items-center justify-around">
        {navItems.map((item) => (
          <Link
            key={item.name}
            to={item.href}
            className="flex flex-col items-center justify-center gap-0.5 py-1 px-3 rounded-xl text-neutral-400 hover:text-white transition-colors group"
            activeProps={{
              className: "text-white font-semibold",
            }}
          >
            {/* Active indicator pill */}
            <div className="h-[3px] w-0 group-[.text-white]:w-4 bg-primary rounded-full transition-all duration-200" />
            <item.icon className="size-5 transition-transform group-active:scale-95" />
            <span className="text-[10px] tracking-tight font-medium">{item.name}</span>
          </Link>
        ))}
      </div>
    </nav>
  );
};

export default BottomNavBar;
