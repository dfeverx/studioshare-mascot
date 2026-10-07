// Web companion agent for StudioShare mascot
// Handles browser tab background monitoring, document title/favicon mood indicators,
// and browser Web Notifications.

export type AgentMood =
  | 'idle'
  | 'uploading'
  | 'searching'
  | 'scanning'
  | 'careful'
  | 'celebrating'
  | 'proud'
  | 'thinking'
  | 'oops'
  | string;

export interface WebAgentTask {
  id: string;
  title: string;
  detail?: string;
  progress: number; // 0..1
  mood?: AgentMood;
}

export interface WebAgentWarning {
  id: string;
  title: string;
  message: string;
  mood?: AgentMood;
}

export interface WebAgentAlert {
  id: string;
  title: string;
  message: string;
  mood?: AgentMood;
}

type Listener = () => void;

class WebMascotAgent {
  private tasks = new Map<string, WebAgentTask>();
  private warnings = new Map<string, WebAgentWarning>();
  private activeAlert: WebAgentAlert | null = null;
  private listeners = new Set<Listener>();
  private originalTitle = typeof document !== 'undefined' ? document.title : 'StudioShare';
  private alertTimeout: any = null;

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('visibilitychange', () => {
        this.updateTabPresentation();
      });
    }
  }

  /** Report progress of a background web task (upload, AI processing, export). */
  reportProgress(taskId: string, title: string, progress: number, detail = '', mood: AgentMood = 'uploading') {
    this.tasks.set(taskId, { id: taskId, title, progress, detail, mood });
    this.notify();
    this.updateTabPresentation();
  }

  /** Mark a task completed, optionally surfacing a browser notification if tab is hidden. */
  completeTask(taskId: string, celebrationTitle?: string, celebrationMessage?: string) {
    this.tasks.delete(taskId);
    if (celebrationTitle) {
      this.postAlert(`done_${taskId}`, celebrationTitle, celebrationMessage || 'Completed successfully', 'celebrating');
    } else {
      this.notify();
      this.updateTabPresentation();
    }
  }

  /** Post a persistent warning (network lost, session expiry warning, etc.). */
  postWarning(id: string, title: string, message: string, mood: AgentMood = 'careful') {
    this.warnings.set(id, { id, title, message, mood });
    this.notify();
    this.updateTabPresentation();
    this.tryShowWebNotification(`⚠️ ${title}`, message);
  }

  dismissWarning(id: string) {
    this.warnings.delete(id);
    this.notify();
    this.updateTabPresentation();
  }

  /** Post a celebratory or informational milestone alert. */
  postAlert(id: string, title: string, message: string, mood: AgentMood = 'celebrating', durationMs = 4500) {
    this.activeAlert = { id, title, message, mood };
    this.notify();
    this.updateTabPresentation();
    this.tryShowWebNotification(`✨ ${title}`, message);

    if (this.alertTimeout) clearTimeout(this.alertTimeout);
    this.alertTimeout = setTimeout(() => {
      if (this.activeAlert?.id === id) {
        this.activeAlert = null;
        this.notify();
        this.updateTabPresentation();
      }
    }, durationMs);
  }

  /** Current active mood of the mascot agent. */
  getCurrentMood(): AgentMood {
    if (this.activeAlert) return this.activeAlert.mood ?? 'celebrating';
    if (this.warnings.size > 0) return Array.from(this.warnings.values())[0].mood ?? 'careful';
    if (this.tasks.size > 0) return Array.from(this.tasks.values())[0].mood ?? 'uploading';
    return 'idle';
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify() {
    this.listeners.forEach((l) => l());
  }

  private updateTabPresentation() {
    if (typeof document === 'undefined') return;

    if (this.warnings.size > 0) {
      const w = Array.from(this.warnings.values())[0];
      document.title = `⚠️ ${w.title} | StudioShare`;
      return;
    }

    if (this.tasks.size > 0) {
      const t = Array.from(this.tasks.values())[0];
      const pct = Math.round(t.progress * 100);
      document.title = `(${pct}%) ${t.title} | StudioShare`;
      return;
    }

    if (this.activeAlert) {
      document.title = `✨ ${this.activeAlert.title} | StudioShare`;
      return;
    }

    document.title = this.originalTitle;
  }

  private tryShowWebNotification(title: string, body: string) {
    if (typeof window === 'undefined' || !('Notification' in window)) return;
    if (document.visibilityState === 'visible') return; // Only notify if in background

    if (Notification.permission === 'granted') {
      new Notification(title, { body, icon: '/mascot/favicon.ico' });
    } else if (Notification.permission !== 'denied') {
      Notification.requestPermission().then((permission) => {
        if (permission === 'granted') {
          new Notification(title, { body, icon: '/mascot/favicon.ico' });
        }
      });
    }
  }
}

export const mascotAgent = new WebMascotAgent();
