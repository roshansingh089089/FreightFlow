import { DashboardPreview } from './DashboardPreview';
import { HeroWorkflowCallouts } from './HeroWorkflowCallouts';

export function HeroProductShowcase() {
  return (
    <div className="ff-hero-stage">
      <div className="ff-hero-dashboard-glow" aria-hidden="true" />
      <div className="ff-hero-product-frame">
        <div className="ff-hero-product-float">
          <div className="ff-hero-product-tilt">
            <DashboardPreview />
          </div>
        </div>
      </div>
      <HeroWorkflowCallouts />
    </div>
  );
}
