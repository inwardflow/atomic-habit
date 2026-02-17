import React from 'react';
import { User } from 'lucide-react';
import clsx from 'clsx';
import type { Goal } from '../types/index';

interface GoalListProps {
  goals: Goal[];
}

const GoalList: React.FC<GoalListProps> = ({ goals }) => {
  if (goals.length === 0) {
    return null;
  }

  return (
    <div className="mb-12">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">My Identity Journeys</h2>
          <p className="text-slate-500 dark:text-slate-400 mt-1">Focus on who you are becoming, not just what you want to achieve.</p>
        </div>
      </div>
      
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {goals.map((goal) => {
            // Extract identity if possible (Coach often puts it in description)
            // Or just use the goal name as the "Mission"
            return (
              <div 
                key={goal.id} 
                className="group bg-white dark:bg-slate-800 overflow-hidden shadow-sm rounded-2xl border border-slate-200 dark:border-slate-700 hover:shadow-md hover:border-indigo-200 dark:hover:border-indigo-700 transition-all duration-300 flex flex-col h-full"
              >
                <div className="p-6 flex-1 flex flex-col">
                  <div className="flex items-start justify-between mb-4">
                    <div className="p-2 bg-indigo-50 dark:bg-indigo-900/30 rounded-lg text-indigo-600 dark:text-indigo-400 group-hover:bg-indigo-600 group-hover:text-white transition-colors">
                      <User className="w-5 h-5" />
                    </div>
                    <span className={clsx(
                      "text-xs font-semibold px-2.5 py-0.5 rounded-full border",
                      goal.status === 'COMPLETED' 
                        ? "bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400 border-green-200 dark:border-green-900/30" 
                        : "bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-400 border-blue-200 dark:border-blue-900/30"
                    )}>
                      {goal.status}
                    </span>
                  </div>

                  <h3 className="text-lg font-bold text-slate-900 dark:text-white mb-2">{goal.name}</h3>
                  <p className="text-sm text-slate-600 dark:text-slate-300 mb-6 italic border-l-2 border-indigo-100 dark:border-indigo-900/50 pl-3">"{goal.description}"</p>
                  
                  <div className="mt-auto">
                    <h4 className="text-xs font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider mb-3">Your Daily System</h4>
                    <ul className="space-y-3">
                      {goal.habits && goal.habits.slice(0, 3).map(habit => (
                        <li key={habit.id} className="text-sm text-slate-700 dark:text-slate-300 flex items-start">
                          <span className={clsx(
                            "w-1.5 h-1.5 rounded-full mr-2.5 mt-1.5 flex-shrink-0 transition-colors",
                            habit.completedToday ? "bg-green-500" : "bg-slate-300 dark:bg-slate-600"
                          )} />
                          <span className={clsx("truncate flex-1", habit.completedToday && "text-slate-400 dark:text-slate-500 line-through")}>
                            {habit.name}
                          </span>
                        </li>
                      ))}
                      {goal.habits && goal.habits.length > 3 && (
                        <li className="text-xs text-slate-400 dark:text-slate-500 italic pl-4">+ {goal.habits.length - 3} more habits</li>
                      )}
                    </ul>
                  </div>
                </div>
                
                {/* Footer with minimal deadline emphasis */}
                <div className="bg-slate-50 dark:bg-slate-900/50 px-6 py-3 border-t border-slate-100 dark:border-slate-700 flex items-center justify-between text-xs text-slate-400 dark:text-slate-500">
                    <span className="font-medium text-indigo-400">Active Journey</span>
                    <div className="flex items-center">
                         <span>Target: {goal.endDate || 'Ongoing'}</span>
                    </div>
                </div>
              </div>
            );
        })}
      </div>
    </div>
  );
};

export default GoalList;
