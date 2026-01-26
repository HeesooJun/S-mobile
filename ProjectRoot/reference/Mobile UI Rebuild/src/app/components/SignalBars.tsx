interface SignalBarsProps {
  strength?: number; // 0-4
  variant?: 'green' | 'gray';
  className?: string;
}

export function SignalBars({ strength = 4, variant = 'green', className = '' }: SignalBarsProps) {
  const color = variant === 'green' ? '#00ff9f' : '#6b7280';
  
  return (
    <div className={`flex items-end gap-0.5 ${className}`}>
      {[1, 2, 3, 4].map((bar) => (
        <div
          key={bar}
          className="w-1"
          style={{
            height: `${bar * 4}px`,
            backgroundColor: bar <= strength ? color : 'rgba(107, 114, 128, 0.3)',
            borderRadius: '1px'
          }}
        />
      ))}
    </div>
  );
}
