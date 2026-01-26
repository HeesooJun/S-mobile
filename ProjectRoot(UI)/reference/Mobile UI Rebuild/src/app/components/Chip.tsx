interface ChipProps {
  children: React.ReactNode;
  variant?: 'red' | 'green' | 'gray';
  className?: string;
}

export function Chip({ children, variant = 'gray', className = '' }: ChipProps) {
  const colors = {
    red: 'bg-red-500/15 border-red-500/60 text-red-400',
    green: 'bg-green-500/15 border-green-400/60 text-green-300',
    gray: 'bg-white/5 border-gray-500/50 text-gray-400'
  };

  return (
    <div className={`inline-block px-4 py-1.5 rounded-full border text-xs font-medium ${colors[variant]} ${className}`}>
      {children}
    </div>
  );
}
