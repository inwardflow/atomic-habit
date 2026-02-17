import React, { useEffect, useState } from 'react';
import api from '../api/axios';
import { Heart, ArrowLeft, Quote, Sparkles, Trash2, Edit2, X, Check } from 'lucide-react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import toast from 'react-hot-toast';

interface MoodLog {
    id: number;
    moodType: string;
    note: string;
    createdAt: string;
}

const GratitudeJar = () => {
    const [logs, setLogs] = useState<MoodLog[]>([]);
    const [loading, setLoading] = useState(true);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [editContent, setEditContent] = useState('');

    useEffect(() => {
        api.get('/moods/gratitude')
           .then(res => setLogs(res.data))
           .catch(err => console.error(err))
           .finally(() => setLoading(false));
    }, []);

    const handleDelete = async (id: number) => {
        if (!window.confirm('Are you sure you want to delete this memory?')) return;
        try {
            await api.delete(`/moods/${id}`);
            setLogs(logs.filter(log => log.id !== id));
            toast.success('Memory deleted.');
        } catch (e) {
            toast.error('Could not delete.');
        }
    };

    const startEdit = (log: MoodLog) => {
        setEditingId(log.id);
        setEditContent(log.note);
    };

    const cancelEdit = () => {
        setEditingId(null);
        setEditContent('');
    };

    const saveEdit = async (id: number) => {
        if (!editContent.trim()) return;
        try {
            await api.put(`/moods/${id}`, { note: editContent });
            setLogs(logs.map(log => log.id === id ? { ...log, note: editContent } : log));
            setEditingId(null);
            toast.success('Memory updated.');
        } catch (e) {
            toast.error('Could not update.');
        }
    };

    return (
        <div className="min-h-screen bg-amber-50 dark:bg-slate-900 transition-colors duration-500">
            <div className="max-w-4xl mx-auto px-4 py-8">
                <Link to="/dashboard" className="inline-flex items-center text-amber-700 dark:text-amber-400 hover:text-amber-900 dark:hover:text-amber-200 mb-8 transition-colors">
                    <ArrowLeft className="w-4 h-4 mr-2" /> Back to Dashboard
                </Link>

                <div className="text-center mb-16">
                    <div className="w-20 h-20 bg-amber-100 dark:bg-amber-900/30 rounded-full flex items-center justify-center mx-auto mb-6 text-amber-600 dark:text-amber-400 shadow-xl shadow-amber-100 dark:shadow-none">
                        <Heart className="w-10 h-10 fill-amber-200 dark:fill-amber-800" />
                    </div>
                    <h1 className="text-4xl font-serif font-bold text-amber-900 dark:text-amber-100 mb-4">The Gratitude Jar</h1>
                    <p className="text-amber-700 dark:text-amber-300/80 max-w-lg mx-auto leading-relaxed">
                        "When you feel like you have nothing, look inside here. <br/>
                        These are the moments you've collected."
                    </p>
                </div>

                {loading ? (
                    <div className="text-center text-amber-400">Opening jar...</div>
                ) : logs.length === 0 ? (
                    <div className="text-center py-20 border-2 border-dashed border-amber-200 dark:border-amber-900/50 rounded-3xl">
                        <Sparkles className="w-12 h-12 text-amber-300 mx-auto mb-4" />
                        <p className="text-amber-600 dark:text-amber-400">The jar is empty, but your life isn't.<br/>Go add your first memory.</p>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 masonry-grid">
                        {logs.map((log, idx) => (
                            <motion.div 
                                key={log.id}
                                initial={{ opacity: 0, y: 20 }}
                                animate={{ opacity: 1, y: 0 }}
                                transition={{ delay: idx * 0.1 }}
                                className="bg-white dark:bg-slate-800 p-8 rounded-2xl shadow-sm border border-amber-100 dark:border-slate-700 hover:shadow-md transition-all relative overflow-hidden group"
                            >
                                <Quote className="absolute top-4 right-4 w-8 h-8 text-amber-50 dark:text-slate-700 -z-0 group-hover:text-amber-100 dark:group-hover:text-slate-600 transition-colors" />
                                
                                {editingId === log.id ? (
                                    <div className="relative z-10">
                                        <textarea 
                                            value={editContent}
                                            onChange={(e) => setEditContent(e.target.value)}
                                            className="w-full p-2 border rounded-md dark:bg-slate-700 dark:text-white mb-2"
                                            rows={3}
                                        />
                                        <div className="flex gap-2 justify-end">
                                            <button onClick={cancelEdit} className="p-1 text-slate-400 hover:text-slate-600"><X className="w-4 h-4" /></button>
                                            <button onClick={() => saveEdit(log.id)} className="p-1 text-green-500 hover:text-green-600"><Check className="w-4 h-4" /></button>
                                        </div>
                                    </div>
                                ) : (
                                    <>
                                        <p className="text-lg text-slate-700 dark:text-slate-200 font-medium mb-4 relative z-10 leading-relaxed pr-8">
                                            "{log.note}"
                                        </p>
                                        <div className="flex justify-between items-center">
                                            <div className="text-xs text-amber-400 dark:text-amber-600/70 font-medium uppercase tracking-wider">
                                                {new Date(log.createdAt).toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
                                            </div>
                                            <div className="flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity z-10">
                                                <button onClick={() => startEdit(log)} className="text-slate-400 hover:text-indigo-500"><Edit2 className="w-4 h-4" /></button>
                                                <button onClick={() => handleDelete(log.id)} className="text-slate-400 hover:text-red-500"><Trash2 className="w-4 h-4" /></button>
                                            </div>
                                        </div>
                                    </>
                                )}
                            </motion.div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

export default GratitudeJar;
