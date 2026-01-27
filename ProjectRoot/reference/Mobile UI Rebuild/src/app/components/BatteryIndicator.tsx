interface BatteryIndicatorProps {
  level: number; // 1-100
  className?: string;
}

export function BatteryIndicator({ level, className = '' }: BatteryIndicatorProps) {
  const getVariant = () => {
    if (level >= 61) return 'high';
    if (level >= 21) return 'mid';
    return 'low';
  };

  const variant = getVariant();
  const colors = {
    high: '#00ff9f',
    mid: '#fbbf24',
    low: '#ff3b3b'
  };

  const fillWidth = Math.max(2, (level / 100) * 36);

  return (
    <div className={`flex items-center gap-1 ${className}`}>
      <div className="relative w-11 h-6 rounded-sm border-2 flex items-center justify-start p-0.5" style={{ borderColor: colors[variant] }}>
        <div 
          className="h-full rounded-sm transition-all"
          style={{ 
            width: `${fillWidth}px`,
            backgroundColor: colors[variant]
          }}
        />
      </div>
      <div className="w-1 h-3 rounded-r" style={{ backgroundColor: colors[variant] }} />
    </div>
  );
}
