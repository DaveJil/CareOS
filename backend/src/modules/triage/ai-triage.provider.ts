import { TriageUrgency } from '@prisma/client';
import type { ConfigService } from '@nestjs/config';

export type TriageInput = {
  symptomsText: string;
  questionnaire?: Record<string, unknown>;
};

export type TriageProviderResult = {
  provider: string;
  urgency: TriageUrgency;
  riskScore: number;
  suggestedSpecialty: string;
  recommendedAction: string;
  rationale: string;
};

export abstract class AiTriageProvider {
  abstract assess(input: TriageInput): Promise<TriageProviderResult>;
}

export class ExternalAiTriageProvider extends AiTriageProvider {
  constructor(private readonly config: ConfigService) {
    super();
  }

  async assess(input: TriageInput): Promise<TriageProviderResult> {
    const apiKey = this.config.getOrThrow<string>('GEMINI_API_KEY');
    const model = this.config.get<string>('GEMINI_MODEL', 'gemini-2.5-flash');
    const apiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;
    const response = await globalThis.fetch(apiUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        contents: [
          {
            role: 'user',
            parts: [
              {
                text:
                  'You are CareOS clinical triage support. Return JSON only. Do not diagnose. ' +
                  'Classify urgency as low, medium, or high and recommend a safe next action.\n' +
                  JSON.stringify({
                    symptomsText: input.symptomsText,
                    questionnaire: input.questionnaire ?? {},
                  }),
              },
            ],
          },
        ],
        generationConfig: {
          responseMimeType: 'application/json',
          responseSchema: {
            type: 'object',
            properties: {
              urgency: { type: 'string', enum: ['low', 'medium', 'high'] },
              riskScore: { type: 'integer' },
              suggestedSpecialty: { type: 'string' },
              recommendedAction: { type: 'string' },
              rationale: { type: 'string' },
            },
            required: [
              'urgency',
              'riskScore',
              'suggestedSpecialty',
              'recommendedAction',
              'rationale',
            ],
          },
        },
        safetySettings: [
          { category: 'HARM_CATEGORY_DANGEROUS_CONTENT', threshold: 'BLOCK_MEDIUM_AND_ABOVE' },
          { category: 'HARM_CATEGORY_HARASSMENT', threshold: 'BLOCK_MEDIUM_AND_ABOVE' },
          { category: 'HARM_CATEGORY_HATE_SPEECH', threshold: 'BLOCK_MEDIUM_AND_ABOVE' },
          { category: 'HARM_CATEGORY_SEXUALLY_EXPLICIT', threshold: 'BLOCK_MEDIUM_AND_ABOVE' },
        ],
      }),
    });

    if (!response.ok) {
      throw new Error('AI triage provider failed assessment.');
    }

    const payload = (await response.json()) as {
      candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
    };
    const text = payload.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!text) {
      throw new Error('AI triage provider returned an empty response.');
    }
    const result = JSON.parse(text) as Partial<TriageProviderResult>;
    return {
      provider: `gemini:${model}`,
      urgency: this.toUrgency(result.urgency),
      riskScore: Number(result.riskScore ?? 50),
      suggestedSpecialty: result.suggestedSpecialty ?? 'General Practice',
      recommendedAction: result.recommendedAction ?? 'Book a clinician consultation.',
      rationale: result.rationale ?? 'External triage provider response.',
    };
  }

  private toUrgency(value: unknown): TriageUrgency {
    if (
      value === TriageUrgency.low ||
      value === TriageUrgency.medium ||
      value === TriageUrgency.high
    ) {
      return value;
    }
    return TriageUrgency.medium;
  }
}

const HIGH_RISK_TERMS = [
  'chest pain',
  'difficulty breathing',
  'shortness of breath',
  'seizure',
  'unconscious',
  'severe bleeding',
  'stroke',
  'suicide',
];

const MEDIUM_RISK_TERMS = [
  'fever',
  'vomiting',
  'weakness',
  'dizziness',
  'abdominal pain',
  'headache',
  'infection',
];

export class MockAiTriageProvider extends AiTriageProvider {
  async assess(input: TriageInput): Promise<TriageProviderResult> {
    const normalized = input.symptomsText.toLowerCase();
    const highHits = HIGH_RISK_TERMS.filter((term) => normalized.includes(term));
    const mediumHits = MEDIUM_RISK_TERMS.filter((term) => normalized.includes(term));
    const painScore = Number(input.questionnaire?.painScore ?? 0);
    const temperature = Number(input.questionnaire?.temperatureCelsius ?? 0);
    const breathingDifficulty = input.questionnaire?.breathingDifficulty === true;

    let riskScore = highHits.length * 30 + mediumHits.length * 12;
    if (painScore >= 8) {
      riskScore += 20;
    }
    if (temperature >= 39) {
      riskScore += 18;
    }
    if (breathingDifficulty) {
      riskScore += 35;
    }
    riskScore = Math.min(100, riskScore);

    const urgency =
      riskScore >= 70
        ? TriageUrgency.high
        : riskScore >= 30
          ? TriageUrgency.medium
          : TriageUrgency.low;

    return {
      provider: 'mock-rule-triage-v1',
      urgency,
      riskScore,
      suggestedSpecialty: this.specialtyFor(normalized, urgency),
      recommendedAction: this.actionFor(urgency),
      rationale: this.rationaleFor({
        highHits,
        mediumHits,
        painScore,
        temperature,
        breathingDifficulty,
      }),
    };
  }

  private specialtyFor(symptoms: string, urgency: TriageUrgency): string {
    if (urgency === TriageUrgency.high) {
      return 'Emergency Medicine';
    }
    if (symptoms.includes('skin') || symptoms.includes('rash')) {
      return 'Dermatology';
    }
    if (symptoms.includes('child') || symptoms.includes('baby')) {
      return 'Pediatrics';
    }
    if (symptoms.includes('chest') || symptoms.includes('heart')) {
      return 'Cardiology';
    }
    return 'General Practice';
  }

  private actionFor(urgency: TriageUrgency): string {
    if (urgency === TriageUrgency.high) {
      return 'Seek urgent clinical review now. If symptoms worsen, call emergency services.';
    }
    if (urgency === TriageUrgency.medium) {
      return 'Book a clinician consultation within 24 hours and monitor symptoms.';
    }
    return 'Self-care may be appropriate. Book a routine consult if symptoms persist.';
  }

  private rationaleFor(input: {
    highHits: string[];
    mediumHits: string[];
    painScore: number;
    temperature: number;
    breathingDifficulty: boolean;
  }): string {
    const reasons = [
      ...input.highHits.map((term) => `high-risk term: ${term}`),
      ...input.mediumHits.map((term) => `moderate-risk term: ${term}`),
    ];
    if (input.painScore >= 8) {
      reasons.push('severe pain score');
    }
    if (input.temperature >= 39) {
      reasons.push('high fever');
    }
    if (input.breathingDifficulty) {
      reasons.push('breathing difficulty');
    }
    return reasons.length > 0 ? reasons.join('; ') : 'No high-risk indicators detected.';
  }
}
