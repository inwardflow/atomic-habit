export interface Session {
    id: number;
    ipAddress: string;
    deviceInfo: string;
    browser?: string;
    operatingSystem?: string;
    deviceType?: string;
    location?: string;
    lastActive: string;
    isCurrent: boolean;
}

export interface User {
    id?: number;
    email: string;
    identityStatement: string;
    roles: string[];
}

export interface LoginHistory {
    id: number;
    ipAddress: string;
    deviceInfo: string;
    browser?: string;
    operatingSystem?: string;
    deviceType?: string;
    location?: string;
    status: 'SUCCESS' | 'FAILED';
    loginTime: string;
}
