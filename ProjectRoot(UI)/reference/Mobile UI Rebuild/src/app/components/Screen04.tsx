import { Chip } from './Chip';
import { SignalBars } from './SignalBars';
import { PrimaryButton } from './PrimaryButton';
import { MicButton } from './MicButton';

interface Screen04Props {
  onDisconnect?: () => void;
  onChat?: () => void;
}

export function Screen04({ onDisconnect, onChat }: Screen04Props) {
  return (
    <div className="w-[360px] h-[800px] bg-gradient-to-b from-gray-900 via-black to-black rounded-[32px] overflow-hidden shadow-2xl relative">
      {/* Vignette effect */}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(0,255,159,0.03)_0%,rgba(0,0,0,0.7)_100%)] pointer-events-none" />
      
      {/* Content */}
      <div className="relative h-full flex flex-col">
        {/* Header */}
        <div className="flex justify-between items-center px-6 pt-14 pb-4">
          <span className="text-gray-400 font-medium text-sm">Offline</span>
          <span className="text-gray-400 text-sm">12:00</span>
        </div>

        {/* Top Chips */}
        <div className="flex gap-2 px-6 mb-8">
          <Chip variant="green">절전 모드</Chip>
          <Chip variant="green">연결된 조난자 수: 2</Chip>
        </div>

        {/* Center Content */}
        <div className="flex-1 flex flex-col items-center justify-center px-8">
          {/* Battery Percentage */}
          <h1 className="text-white font-black text-[120px] leading-none mb-4">85%</h1>
          <p className="text-gray-500 text-sm mb-12">약 36시간 대기 가능</p>

          {/* Mic Button */}
          <MicButton className="mb-6" />

          {/* Connection Text */}
          <p className="text-green-400 font-medium text-base mb-16">구조자와 연결 확인</p>
        </div>

        {/* Bottom Section */}
        <div className="px-6 pb-10">
          <div className="flex items-center justify-between mb-4">
            <SignalBars strength={4} variant="green" />
            <Chip variant="green">센서 상태 정상 작동</Chip>
          </div>
          
          <div className="flex gap-3 mt-4">
            <PrimaryButton variant="red" className="flex-1" onClick={onDisconnect}>
              연결 끊기
            </PrimaryButton>
            <PrimaryButton variant="green" className="flex-1" onClick={onChat}>
              채팅방
            </PrimaryButton>
          </div>
        </div>
      </div>
    </div>
  );
}
