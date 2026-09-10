import { Link } from "@tanstack/react-router";
import { Home, Radio, Music, Heart, Settings } from "lucide-react";

export const BottomNavBar = () => {
  const navItems = [
    { name: "Home", href: "/", icon: Home },
    { name: "Online", href: "/stream", icon: Radio },
    { name: "Songs", href: "/songs", icon: Music },
    { name: "Favorites", href: "/favorites", icon: Heart },
    { name: "Settings", href: "/settings", icon: Settings },
  ];

  return (
    <nav className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-background/90 backdrop-blur-2xl border-t border-border/40 px-2 pt-1.5 pb-[max(0.375rem,env(safe-area-inset-bottom))]">
      <div className="flex items-center justify-around">
        {navItems.map((item) => (
          <Link
            key={item.name}
            to={item.href}
            className="flex flex-col items-center justify-center gap-0.5 py-1 px-3 rounded-xl text-muted-foreground hover:text-foreground transition-colors group"
            activeProps={{
              className: "text-primary font-semibold",
            }}
          >
            {/* Active indicator pill */}
            <div className="h-[3px] w-0 group-[.text-primary]:w-4 bg-primary rounded-full transition-all duration-200" />
            <item.icon className="size-5" />
            <span className="text-[10px] tracking-tight">{item.name}</span>
          </Link>
        ))}
      </div>
    </nav>
  );
};

export default BottomNavBar;
