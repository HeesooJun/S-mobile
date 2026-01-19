import { AlertCircle } from 'lucide-react';
import { SecondaryButton } from './SecondaryButton';

interface Screen03Props {
  onPrev?: () => void;
  onNext?: () => void;
}

export function Screen03({ onPrev, onNext }: Screen03Props) {
  return (
    <div className="w-[360px] h-[800px] bg-gradient-to-b from-red-950/40 via-black to-black rounded-[32px] overflow-hidden shadow-2xl relative">
      {/* Red-tinted vignette effect */}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,transparent_0%,rgba(139,0,0,0.3)_100%)] pointer-events-none" />
      
      {/* Content */}
      <div className="relative h-full flex flex-col">
        {/* Header */}
        <div className="flex justify-between items-center px-6 pt-14 pb-4">
          <span className="text-red-500 font-bold text-base">긴급 상황</span>
          <span className="text-red-400 text-sm">15%</span>
        </div>

        {/* Center Content */}
        <div className="flex-1 flex flex-col items-center justify-center px-8">
          {/* Alert Icon */}
          <div className="mb-8 relative">
            <div className="absolute inset-0 animate-ping opacity-30">
              <AlertCircle size={100} className="text-red-500" strokeWidth={2.5} />
            </div>
            <AlertCircle size={100} className="text-red-500 relative" strokeWidth={2.5} />
          </div>
          
          {/* Title */}
          <h1 className="text-white font-black text-4xl mb-6 text-center">구조 신호 송출</h1>
          
          {/* Description */}
          <p className="text-gray-400 text-sm text-center mb-2">초절전 모드로 신호 전송 중</p>
          <p className="text-gray-400 text-sm text-center mb-20">화면 밝기가 최소화됩니다</p>
        </div>

        {/* Bottom */}
        <div className="px-6 pb-10">
          <div className="flex items-center justify-between">
            <SecondaryButton variant="gray" onClick={onPrev}>이전</SecondaryButton>
            {onNext ? (
              <SecondaryButton variant="red" onClick={onNext}>다음</SecondaryButton>
            ) : (
              <div className="w-20" />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
