import { describe, expect, it } from 'vitest'
import { centsToInput, formatDuration, formatPrice, lessonCount, parsePriceToCents } from './format'

describe('formatPrice', () => {
  it('formats euro cents the Latvian way', () => {
    // Intl uses a non-breaking space before the euro sign
    expect(formatPrice(4900).replace(/\s/g, ' ')).toBe('49,00 €')
    expect(formatPrice(0).replace(/\s/g, ' ')).toBe('0,00 €')
  })
})

describe('formatDuration', () => {
  it('shows minutes and hours', () => {
    expect(formatDuration(300)).toBe('5 min')
    expect(formatDuration(20)).toBe('1 min')
    expect(formatDuration(3600)).toBe('1 h')
    expect(formatDuration(5400)).toBe('1 h 30 min')
    expect(formatDuration(null)).toBe('')
  })
})

describe('lessonCount', () => {
  it('uses Latvian plural forms', () => {
    expect(lessonCount(1)).toBe('1 nodarbība')
    expect(lessonCount(21)).toBe('21 nodarbība')
    expect(lessonCount(2)).toBe('2 nodarbības')
    expect(lessonCount(11)).toBe('11 nodarbību')
    expect(lessonCount(10)).toBe('10 nodarbību')
    expect(lessonCount(0)).toBe('0 nodarbību')
  })
})

describe('parsePriceToCents', () => {
  it('accepts comma or dot and up to two decimals', () => {
    expect(parsePriceToCents('49')).toBe(4900)
    expect(parsePriceToCents('49,9')).toBe(4990)
    expect(parsePriceToCents('49.90')).toBe(4990)
    expect(parsePriceToCents(' 0,29 ')).toBe(29)
  })

  it('rejects anything else', () => {
    for (const bad of ['', '-1', '49,999', 'abc', '1e3', '49 €']) {
      expect(parsePriceToCents(bad)).toBeNull()
    }
  })

  it('round-trips with centsToInput', () => {
    expect(centsToInput(4990)).toBe('49,90')
    expect(parsePriceToCents(centsToInput(5))).toBe(5)
  })
})
