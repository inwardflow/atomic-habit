import 'i18next';
import translation from '../locales/zh/translation.json';
import habits from '../locales/zh/habits.json';
import analytics from '../locales/zh/analytics.json';
import coach from '../locales/zh/coach.json';
import panic from '../locales/zh/panic.json';
import timer from '../locales/zh/timer.json';
import identity_modal from '../locales/zh/identity_modal.json';
import gratitude_jar from '../locales/zh/gratitude_jar.json';

declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation';
    resources: {
      translation: typeof translation;
      habits: typeof habits;
      analytics: typeof analytics;
      coach: typeof coach;
      panic: typeof panic;
      timer: typeof timer;
      identity_modal: typeof identity_modal;
      gratitude_jar: typeof gratitude_jar;
    };
  }
}
