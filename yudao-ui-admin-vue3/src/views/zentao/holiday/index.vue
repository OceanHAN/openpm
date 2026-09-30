<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="节假日有两种记录：假期与补班"
      description="type=假期 的日期不算工作日（即使是周一到周五）；type=补班 的日期算工作日（即使是周六周日，即调休）。判定优先级：补班 > 假期 > 周末。它是全项目「工作日」的唯一出口 —— 燃尽图的横轴就按它来：国庆假期不占格子、调休的周六要占。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="70px">
      <el-form-item label="年份">
        <el-select v-model="query.year" class="!w-140px" @change="load">
          <el-option v-for="y in years" :key="y" :label="y" :value="y" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.type" clearable placeholder="全部" class="!w-140px" @change="load">
          <el-option label="假期" value="holiday" />
          <el-option label="补班" value="working" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:holiday:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" empty-text="这一年还没有节假日">
      <el-table-column label="编号" prop="id" width="80" align="center" />
      <el-table-column label="名称" prop="name" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.type === 'holiday' ? 'warning' : 'success'" size="small">
            {{ row.type === 'holiday' ? '假期' : '补班' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="起止" min-width="240">
        <template #default="{ row }">
          {{ row.begin }}<span v-if="row.end !== row.begin"> ~ {{ row.end }}</span>
          <span class="ml-8px text-gray-400">{{ daysOf(row) }} 天</span>
        </template>
      </el-table-column>
      <el-table-column label="描述" prop="desc" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm('edit', row)" v-hasPermi="['zentao:holiday:update']">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)" v-hasPermi="['zentao:holiday:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <ContentWrap>
    <el-card shadow="never">
      <template #header><span class="font-bold">工作日试算</span></template>
      <el-form :inline="true" label-width="90px">
        <el-form-item label="开始">
          <el-date-picker v-model="probe.begin" type="date" value-format="YYYY-MM-DD" class="!w-170px" />
        </el-form-item>
        <el-form-item label="结束">
          <el-date-picker v-model="probe.end" type="date" value-format="YYYY-MM-DD" class="!w-170px" />
          <span class="ml-8px text-12px text-gray-400">左闭右开（不含结束日；与开始相同则算那一天）</span>
        </el-form-item>
        <el-form-item>
          <el-button :disabled="!probe.begin || !probe.end" @click="runProbe">
            <Icon icon="ep:magic-stick" class="mr-5px" /> 试算
          </el-button>
        </el-form-item>
      </el-form>
      <div v-if="probeResult">
        实际工作日 <strong>{{ probeResult.count }}</strong> 天：
        <el-tag v-for="d in probeResult.days" :key="d" size="small" class="mr-5px mb-5px">{{ d }}</el-tag>
        <span v-if="probeResult.count === 0" class="text-gray-400">这几天全是假期或周末</span>
      </div>
    </el-card>
  </ContentWrap>

  <el-dialog v-model="formVisible" :title="formTitle" width="560px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="例如 国庆节 / 春节调休" />
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-radio-group v-model="form.type">
          <el-radio label="holiday">假期（不算工作日）</el-radio>
          <el-radio label="working">补班（算工作日）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="开始" prop="begin">
        <el-date-picker v-model="form.begin" type="date" value-format="YYYY-MM-DD" class="!w-200px" />
      </el-form-item>
      <el-form-item label="结束" prop="end">
        <el-date-picker v-model="form.end" type="date" value-format="YYYY-MM-DD" class="!w-200px" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.desc" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取 消</el-button>
      <el-button type="primary" :loading="saving" @click="submitForm">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as HolidayApi from '@/api/zentao/holiday'

defineOptions({ name: 'ZentaoHoliday' })

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const list = ref<HolidayApi.HolidayVO[]>([])
const years = ref<string[]>([])
const query = reactive<{ year?: string; type?: string }>({})

const formVisible = ref(false)
const formTitle = ref('')
const formRef = ref()
const form = ref<HolidayApi.HolidayVO>({ name: '', type: 'holiday' })
const rules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  begin: [{ required: true, message: '开始日期不能为空', trigger: 'change' }],
  end: [{ required: true, message: '结束日期不能为空', trigger: 'change' }]
}

const probe = reactive<{ begin?: string; end?: string }>({})
const probeResult = ref<{ count: number; days: string[] }>()

// 天数含首尾（假期「10-01 ~ 10-07」是 7 天）
const daysOf = (row: HolidayApi.HolidayVO) => {
  if (!row.begin || !row.end) return 0
  return Math.round((new Date(row.end).getTime() - new Date(row.begin).getTime()) / 86400000) + 1
}

const load = async () => {
  loading.value = true
  try {
    list.value = await HolidayApi.getHolidayList({ year: query.year, type: query.type })
  } finally {
    loading.value = false
  }
}

const openForm = (type: string, row?: HolidayApi.HolidayVO) => {
  form.value = type === 'edit' && row ? { ...row } : { name: '', type: 'holiday' }
  formTitle.value = type === 'edit' ? '编辑节假日' : '新增节假日'
  formVisible.value = true
}

const submitForm = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.value.id) {
      await HolidayApi.updateHoliday(form.value)
      message.success('已保存')
    } else {
      await HolidayApi.createHoliday(form.value)
      message.success('已创建')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: HolidayApi.HolidayVO) => {
  await message.delConfirm(`确认删除「${row.name}」？`)
  await HolidayApi.deleteHoliday(row.id!)
  message.success('已删除')
  await load()
}

const runProbe = async () => {
  probeResult.value = await HolidayApi.getWorkingDays(probe.begin!, probe.end!)
}

onMounted(async () => {
  years.value = await HolidayApi.getHolidayYears()
  query.year = years.value[0]
  await load()
})
</script>
