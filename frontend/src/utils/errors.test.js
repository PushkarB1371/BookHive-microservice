import { describe, expect, it } from 'vitest';
import { getErrorMessage } from './errors';

describe('getErrorMessage', () => {
  it('uses the message from the backend error body', () => {
    const err = { response: { status: 400, data: { message: 'Email already registered' } } };
    expect(getErrorMessage(err, 'fallback')).toBe('Email already registered');
  });

  it('accepts a plain string body', () => {
    const err = { response: { status: 400, data: 'Bad thing' } };
    expect(getErrorMessage(err, 'fallback')).toBe('Bad thing');
  });

  it('never returns an object (which would crash React when rendered)', () => {
    const err = { response: { status: 500, data: { timestamp: 'x', status: 500, error: 'Internal Server Error' } } };
    expect(typeof getErrorMessage(err, 'Something went wrong')).toBe('string');
    expect(getErrorMessage(err, 'Something went wrong')).toBe('Something went wrong');
  });

  it('explains a network failure', () => {
    expect(getErrorMessage(new Error('Network Error'), 'fallback')).toMatch(/cannot reach the server/i);
  });

  it('asks for a new login on 401', () => {
    expect(getErrorMessage({ response: { status: 401, data: '' } }, 'fallback')).toMatch(/log in again/i);
  });

  it('falls back when there is nothing useful', () => {
    expect(getErrorMessage({ response: { status: 500, data: null } }, 'fallback')).toBe('fallback');
  });
});
