import { ArrowRight } from 'lucide-react';
import { SecondaryButton } from './SecondaryButton';
import { RecordButton } from './RecordButton';

interface Screen05Props {
  onPrev?: () => void;
}

export function Screen05({ onPrev }: Screen05Props) {
  return (
    <div className="w-[360px] h-[800px] bg-gradient-to-b from-gray-900 via-black to-black rounded-[32px] overflow-hidden shadow-2xl relative">
      {/* Vignette effect */}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(0,255,159,0.02)_0%,rgba(0,0,0,0.7)_100%)] pointer-events-none" />
      
      {/* Content */}
      <div className="relative h-full flex flex-col">
        {/* Status Bar */}
        <div className="flex justify-between items-center px-6 pt-14 pb-2">
          <span className="text-green-400 font-medium text-sm">Connected</span>
          <span className="text-gray-400 text-sm">12:05</span>
        </div>

        {/* Header Bar */}
        <div className="flex items-center gap-3 px-6 py-4 border-b border-gray-800">
          <SecondaryButton variant="gray" onClick={onPrev}>이전</SecondaryButton>
          <h2 className="text-white font-bold text-base flex-1 text-center">김싸피의 채팅방</h2>
          <div className="w-16" /> {/* Spacer for centering */}
        </div>

        {/* Chat Messages */}
        <div className="flex-1 overflow-y-auto px-6 py-6 space-y-3">
          {/* Received message */}
          <div className="flex justify-start">
            <div className="bg-gray-800/80 rounded-2xl rounded-tl-sm px-4 py-2.5 max-w-[240px]">
              <p className="text-gray-300 text-sm">안녕하세요</p>
            </div>
          </div>

          {/* Sent message */}
          <div className="flex justify-end">
            <div className="bg-green-500/20 border border-green-400/30 rounded-2xl rounded-tr-sm px-4 py-2.5 max-w-[240px]">
              <p className="text-green-300 text-sm">네, 안녕하세요</p>
            </div>
          </div>

          {/* Received message */}
          <div className="flex justify-start">
            <div className="bg-gray-800/80 rounded-2xl rounded-tl-sm px-4 py-2.5 max-w-[240px]">
              <p className="text-gray-300 text-sm">현재 위치가 어디신가요?</p>
            </div>
          </div>

          {/* Sent message */}
          <div className="flex justify-end">
            <div className="bg-green-500/20 border border-green-400/30 rounded-2xl rounded-tr-sm px-4 py-2.5 max-w-[240px]">
              <p className="text-green-300 text-sm">GPS 좌표 전송했습니다</p>
            </div>
          </div>
        </div>

        {/* Input Bar */}
        <div className="px-6 pb-8 pt-4">
          <div className="flex items-center gap-3 bg-gray-800/50 rounded-full px-5 py-3 border border-gray-700/50">
            <input 
              type="text" 
              placeholder="메시지 입력..."
              className="flex-1 bg-transparent text-white text-sm placeholder-gray-500 outline-none"
            />
            <RecordButton state="idle" />
            <button className="w-11 h-11 rounded-full bg-green-500/20 border-2 border-green-400 flex items-center justify-center shadow-[0_0_15px_rgba(0,255,159,0.25)] hover:scale-105 transition-all">
              <ArrowRight size={18} className="text-green-400" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
