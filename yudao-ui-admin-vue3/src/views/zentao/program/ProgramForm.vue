<template>
  <el-dialog v-model="visible" :title="form.id ? '修改项目集' : '新建项目集'" width="640px" append-to-body>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="项目集、项目、执行是同一张表"
      description="禅道里 TABLE_PROGRAM / TABLE_PROJECT / TABLE_EXECUTION 都指向 zt_project。项目集靠 type='program' 区分，项目用 parent 指向所属项目集；层级 path 是逗号格式（,9001,9002,），顶级项目集 grade=1。"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="上级项目集" prop="parent">
        <el-select v-model="form.parent" filterable clearable placeholder="不选 = 顶级项目集" class="!w-320px">
          <el-option label="（顶级项目集）" :value="0" />
          <el-option v-for="p in parentOptions" :key="p.id" :label="p.name!" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="项目集名称（同一级下唯一）" />
      </el-form-item>
      <el-form-item label="代号" prop="code">
        <el-input v-model="form.code" placeholder="可选" class="!w-240px" />
      </el-form-item>
      <el-form-item label="负责人" prop="PM">
        <el-select v-model="form.PM" filterable allow-create default-first-option clearable
                   placeholder="禅道 PM" class="!w-240px">
          <el-option v-for="u in userList" :key="u.account" :label="`${u.realname || u.account}（${u.account}）`"
                     :value="u.account!" />
        </el-select>
      </el-form-item>
      <el-form-item label="计划起止" prop="begin">
        <el-date-picker v-model="form.begin" type="date" value-format="YYYY-MM-DD" placeholder="开始" class="!w-160px" />
        <span class="mx-8px">~</span>
        <el-date-picker v-model="form.end" type="date" value-format="YYYY-MM-DD" placeholder="结束" class="!w-160px" />
      </el-form-item>
      <el-form-item label="预算" prop="budget">
        <el-input-number v-model="form.budget" :min="0" :precision="2" :controls="false" class="!w-180px" />
        <el-select v-model="form.budgetUnit" class="!w-100px ml-8px">
          <el-option label="CNY" value="CNY" />
          <el-option label="USD" value="USD" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级" prop="pri">
        <el-input-number v-model="form.pri" :min="1" :max="4" class="!w-140px" />
      </el-form-item>
      <el-form-item label="描述" prop="desc">
        <el-input v-model="form.desc" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as ProgramApi from '@/api/zentao/program'
import * as OrgApi from '@/api/zentao/organization'

defineOptions({ name: 'ZentaoProgramForm' })

const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const parentOptions = ref<ProgramApi.ProgramVO[]>([])
const userList = ref<OrgApi.OrgUserVO[]>([])

const form = reactive<ProgramApi.ProgramVO>({
  id: undefined,
  parent: 0,
  name: '',
  code: '',
  PM: '',
  begin: '',
  end: '',
  budget: 0,
  budgetUnit: 'CNY',
  pri: 1,
  desc: ''
})

const rules = {
  name: [{ required: true, message: '请输入项目集名称', trigger: 'blur' }],
  begin: [{ required: true, message: '请选择计划开始日期', trigger: 'change' }],
  end: [{ required: true, message: '请选择计划结束日期', trigger: 'change' }]
}

const loadOptions = async () => {
  // 编辑时不能把自己或自己的子孙选成上级（后端也会拦）
  parentOptions.value = (await ProgramApi.getProgramList()).filter((p) => p.id !== form.id)
  if (!userList.value.length) {
    try {
      userList.value = await OrgApi.getUserList({})
    } catch {
      userList.value = []
    }
  }
}

const open = async (mode: 'create' | 'edit', row?: ProgramApi.ProgramVO, defaultParent?: number) => {
  if (mode === 'edit' && row) {
    Object.assign(form, {
      id: row.id,
      parent: row.parent ?? 0,
      name: row.name,
      code: row.code,
      PM: row.PM,
      begin: row.begin,
      end: row.end,
      budget: row.budget,
      budgetUnit: row.budgetUnit || 'CNY',
      pri: row.pri ?? 1,
      desc: row.desc
    })
  } else {
    Object.assign(form, {
      id: undefined,
      parent: defaultParent ?? 0,
      name: '',
      code: '',
      PM: '',
      begin: '',
      end: '',
      budget: 0,
      budgetUnit: 'CNY',
      pri: 1,
      desc: ''
    })
  }
  await loadOptions()
  visible.value = true
}

const handleSubmit = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await ProgramApi.updateProgram(form)
      message.success('项目集已修改')
    } else {
      await ProgramApi.createProgram(form)
      message.success('项目集已创建')
    }
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
