import { SecondaryButton } from './SecondaryButton';
import { Chip } from './Chip';
import { SignalBars } from './SignalBars';
import { BatteryIndicator } from './BatteryIndicator';

interface Screen02Props {
  onPrev?: () => void;
  onSos?: () => void;
}

export function Screen02({ onPrev, onSos }: Screen02Props) {
  return (
    <div className="w-[360px] h-[800px] bg-gradient-to-b from-gray-900 via-black to-black rounded-[32px] overflow-hidden shadow-2xl relative">
      {/* Vignette effect */}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(255,255,255,0.02)_0%,rgba(0,0,0,0.7)_100%)] pointer-events-none" />
      
      {/* Content */}
      <div className="relative h-full flex flex-col">
        {/* Header */}
        <div className="flex justify-between items-center px-6 pt-14 pb-4">
          <span className="text-gray-400 font-medium text-sm">Offline</span>
          <span className="text-gray-400 text-sm">12:00</span>
        </div>

        {/* Center Content */}
        <div className="flex-1 flex flex-col items-center justify-center px-8">
          {/* Battery Icon */}
          <BatteryIndicator level={92} className="mb-3" />
          
          {/* Battery Percentage */}
          <h1 className="text-white font-black text-[120px] leading-none mb-4">92%</h1>
          <p className="text-gray-500 text-sm mb-16">약 48시간 대기 가능</p>

          {/* SOS Button */}
          <button
            onClick={onSos}
            className="w-40 h-40 rounded-full bg-red-500/20 border-4 border-red-500 flex items-center justify-center shadow-[0_0_50px_rgba(255,59,59,0.5)] hover:scale-105 transition-all mb-16"
          >
            <span className="text-red-500 font-black text-4xl">SOS</span>
          </button>
        </div>

        {/* Bottom Bar */}
        <div className="px-6 pb-10">
          <div className="flex items-center justify-between mb-4">
            <SecondaryButton variant="gray" onClick={onPrev}>이전</SecondaryButton>
            <Chip variant="green">센서 상태 정상 작동</Chip>
            <SignalBars strength={4} variant="green" />
          </div>
        </div>
      </div>
    </div>
  );
}
