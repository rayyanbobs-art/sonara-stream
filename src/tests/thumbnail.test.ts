import { describe, it, expect } from "vitest";
import { getOptimizedThumbnail } from "../utils/thumbnail";

describe("getOptimizedThumbnail", () => {
  it("handles null, undefined, and empty inputs", () => {
    expect(getOptimizedThumbnail(null)).toBe("");
    expect(getOptimizedThumbnail(undefined)).toBe("");
    expect(getOptimizedThumbnail("")).toBe("");
  });

  it("removes YouTube query parameters and upscales low-res defaults to hqdefault", () => {
    const defaultThumb = "https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg?sqp=-oaymwE2COADEI4CSFXyq4qpAygIARUAAIhCGAFwAcABBvABAfgB_gmAAtAFigIMCAAQARhyIE4oPzAP&rs=AOn4CL...";
    expect(getOptimizedThumbnail(defaultThumb, "list")).toBe("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");

    const mqThumb = "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg?sqp=abc";
    expect(getOptimizedThumbnail(mqThumb, "card")).toBe("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");

    const hqThumb = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg";
    expect(getOptimizedThumbnail(hqThumb)).toBe("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");

    const maxresThumb = "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg";
    expect(getOptimizedThumbnail(maxresThumb)).toBe("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg");
  });

  it("upgrades Google CDN avatars to 512px", () => {
    const avatar = "https://yt3.ggpht.com/a/default-user=s88-c-k-c0x00ffffff-no-rj";
    expect(getOptimizedThumbnail(avatar)).toBe("https://yt3.ggpht.com/a/default-user=s512-c-k-c0x00ffffff-no-rj");

    const userContent = "https://lh3.googleusercontent.com/abc=w120-h120";
    expect(getOptimizedThumbnail(userContent)).toBe("https://lh3.googleusercontent.com/abc=w512-h512");
  });

  it("upgrades Apple Music / iTunes mzstatic images to appropriate dimensions", () => {
    const itunesUrl = "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/a1/b2/c3/source/100x100bb.jpg";
    expect(getOptimizedThumbnail(itunesUrl, "list")).toBe(
      "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/a1/b2/c3/source/300x300bb.jpg"
    );
    expect(getOptimizedThumbnail(itunesUrl, "card")).toBe(
      "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/a1/b2/c3/source/600x600bb.jpg"
    );
    expect(getOptimizedThumbnail(itunesUrl, "full")).toBe(
      "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/a1/b2/c3/source/600x600bb.jpg"
    );
  });

  it("leaves unrecognized URLs untouched", () => {
    const raw = "https://example.com/album_art.png";
    expect(getOptimizedThumbnail(raw)).toBe(raw);
  });
});
