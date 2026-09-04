package no.nav.syfo.varsel.budstikka.infrastructure

const val EVALUERINGS_PAAMINNELSE_EMAIL_TITLE = "Oppdater oppfølgingsplanen"
val EVALUERINGS_PAAMINNELSE_EMAIL_HTML = """
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width: 100%; max-width: 640px; margin: 0 auto; border: 1px solid #d8d8d8; border-radius: 8px; background-color: #ffffff; color: #262626; font-family: Arial, sans-serif;">
      <tbody>
        <tr>
          <td style="padding: 28px 32px; background-color: #004367; color: #ffffff;">
            <span aria-hidden="true" style="margin-right: 12px; font-size: 24px;">&#9993;&#65039;</span>
            <span style="font-size: 24px; font-weight: 700; line-height: 1.3;">Oppdater oppfølgingsplanen</span>
          </td>
        </tr>
        <tr>
          <td style="padding: 32px; font-size: 18px; line-height: 1.5;">
            <p style="margin: 0 0 24px;">Hei,</p>
            <p style="margin: 0 0 32px;">Det er tid for å vurdere om situasjonen til den som er sykmeldt er annerledes enn tidligere, og om det derfor er riktig å gjøre endringer i oppfølgingsplanen. Ta en prat for å finne ut om det er aktuelt nå, eller om dere skal lage en ny avtale litt frem i tid.</p>
            <p style="margin: 0 0 24px; font-weight: 700;">Gå til Min side – arbeidsgiver hos Nav for å oppdatere oppfølgingsplanen.</p>
            <hr style="margin: 0 0 24px; border: 0; border-top: 1px solid #d8d8d8;">
            <p style="margin: 0 0 20px;">Har du spørsmål? Ring oss på 55 55 33 36.</p>
            <p style="margin: 0 0 20px;">Du kan ikke svare på denne meldingen.</p>
            <p style="margin: 0;">Vennlig hilsen Nav</p>
          </td>
        </tr>
      </tbody>
    </table>
""".trimIndent()

const val OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_TITLE = "Start oppfølgingsplan"
val OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_HTML = """
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width: 100%; max-width: 640px; margin: 0 auto; border: 1px solid #d8d8d8; border-radius: 8px; background-color: #ffffff; color: #262626; font-family: Arial, sans-serif;">
      <tbody>
        <tr>
          <td style="padding: 28px 32px; background-color: #004367; color: #ffffff;">
            <span aria-hidden="true" style="margin-right: 12px; font-size: 24px;">&#9993;&#65039;</span>
            <span style="font-size: 24px; font-weight: 700; line-height: 1.3;">Start oppfølgingsplan</span>
          </td>
        </tr>
        <tr>
          <td style="padding: 32px; font-size: 18px; line-height: 1.5;">
            <p style="margin: 0 0 24px;">Hei,</p>
            <p style="margin: 0 0 24px;">Du har en ansatt som er sykmeldt hvor fristen for å lage en oppfølgingsplan nærmer seg.</p>
            <p style="margin: 0 0 24px;">Du trenger ikke ha alle svarene klare. Avtal en prat hvor dere sammen finner ut om noen arbeidsoppgaver er mulig å gjøre i sykmeldingsperioden.</p>
            <p style="margin: 0 0 32px;">Jo tidligere dere gjør dette desto lettere er det for mange å komme tilbake i jobb og at langvarig fravær forebygges.</p>
            <p style="margin: 0 0 16px; font-weight: 700;">Slik gjør du det:</p>
            <ol style="margin: 0 0 24px; padding-left: 28px;">
              <li style="margin: 0 0 12px;">Logg inn på Min side – arbeidsgiver.</li>
              <li style="margin: 0;">🔔 Klikk på bjella. Der finner du meldingen om å lage oppfølgingsplan (evt. meld fra at det ikke er behov nå).</li>
            </ol>
            <hr style="margin: 0 0 24px; border: 0; border-top: 1px solid #d8d8d8;">
            <p style="margin: 0 0 20px;">Har du spørsmål? Ring oss på 55 55 33 36.</p>
            <p style="margin: 0 0 20px;">Du kan ikke svare på denne meldingen.</p>
            <p style="margin: 0;">Vennlig hilsen Nav</p>
          </td>
        </tr>
      </tbody>
    </table>
""".trimIndent()
