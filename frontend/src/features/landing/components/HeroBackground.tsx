import shipArt from '../assets/hero-container-ship.webp';
import worldArt from '../assets/hero-world.svg';

export function HeroBackground() {
  return (
    <div className="ff-hero-background" aria-hidden="true">
      <div className="ff-hero-ambient ff-hero-ambient-left" />
      <div className="ff-hero-ambient ff-hero-ambient-right" />
      <img className="ff-hero-world" src={worldArt} alt="" />
      <img className="ff-hero-ship" src={shipArt} alt="" />
    </div>
  );
}
